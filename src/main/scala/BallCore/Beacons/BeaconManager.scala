// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

import BallCore.Groups.{GroupID, GroupManager}
import BallCore.Storage
import BallCore.Storage.SQLManager
import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all.*
import com.github.plokhotnyuk.rtree2d.core.*
import com.github.plokhotnyuk.rtree2d.core.EuclideanPlane.*
import net.kyori.adventure.text.Component
import org.bukkit.{Location, World}
import org.locationtech.jts.geom.{
    Coordinate,
    GeometryCollection,
    GeometryFactory,
    Polygon,
}
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
import skunk.Session
import skunk.codec.all.*
import skunk.implicits.*
import org.locationtech.jts.io.geojson.{GeoJsonReader, GeoJsonWriter}
import java.text.DecimalFormat
import java.util.UUID
import cats.data.EitherT
import skunk.Transaction
import BallCore.Sigils.BattleManager
import cats.effect.kernel.Deferred
import BallCore.DataStructures.Clock
import java.time.OffsetDateTime

type OwnerID = UUID
type HeartID = UUID
type BeaconID = UUID

case class Point(x: Int, y: Int, z: Int)

private def addPoint(l: Point, r: Point): Point =
    Point(l.x + r.x, l.y + r.y, l.z + r.z)

case class CivBeaconInformation(
    players: List[UUID],
    centroid: Point,
)

val df =
    val it = new DecimalFormat()
    it.setMaximumFractionDigits(1)
    it

enum PolygonAdjustmentError:
    case polygonTooLarge(maximum: Int, actual: Double)
    case heartsNotIncludedInPolygon(at: List[Location])
    case overlapsOneOtherPolygon(
        beaconID: BeaconID,
        groupID: GroupID,
        groupName: String,
        canDeclareWar: Option[Polygon],
    )
    case overlapsMultiplePolygons()

    def explain: Component =
        import BallCore.UI.ChatElements.*

        this match
            case polygonTooLarge(maximum, actual) =>
                trans"errors.polygon-adjustment.too-large".args(
                    maximum.toComponent.color(Colors.teal),
                    df.format(actual).toComponent.color(Colors.teal),
                )
            case heartsNotIncludedInPolygon(at) =>
                val locations = at
                    .map { loc =>
                        txt"${txt(df.format(loc.getX()))
                                .color(Colors.grellow)}/${txt(df.format(loc.getY()))
                                .color(Colors.grellow)}"
                    }
                    .mkComponent(txt", ")
                trans"errors.polygon-adjustment.hearts-not-included".args(
                    locations
                )
            case overlapsOneOtherPolygon(
                    beaconID,
                    groupID,
                    groupName,
                    Some(_),
                ) =>
                trans"errors.polygon-adjustment.overlaps-one-other-can-war".args(
                    groupName.toComponent.color(Colors.grellow)
                )
            case overlapsOneOtherPolygon(beaconID, groupID, groupName, None) =>
                trans"errors.polygon-adjustment.overlaps-one-other-can-not-war".args(
                    groupName.toComponent.color(Colors.grellow)
                )
            case overlapsMultiplePolygons() =>
                trans"errors.polygon-adjustment.overlaps-multiple-others"

object CivBeaconManager:
    def populationToArea(count: Int): Int =
        if count == 1 then (64 * 64) * count
        else if count < 10 then ((64 * 64) * 2 * count) - (64 * 64)
        else ((64 * 64) * count) + 9 * (64 * 64)

trait BeaconManagerHooks:
    def beaconDeletionIncoming(
        beaconID: BeaconID
    )(using Session[IO], Transaction[IO]): IO[Unit]

class IngameBeaconManagerHooks(using battles: BattleManager)
    extends BeaconManagerHooks:
    override def beaconDeletionIncoming(
        beaconID: BeaconID
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for {
            battleList <- battles.battlesThatBeaconIsInvolvedIn(beaconID)
            (offensive, defensive) = battleList
            _ <- offensive.traverse(battles.offensiveResign)
            _ <- defensive.traverse(battles.defensiveResign)
        } yield ()

class CivBeaconManager()(using
    sql: Storage.SQLManager,
    c: Clock,
    hooks: Deferred[IO, BeaconManagerHooks],
)(using GroupManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial CivBeaconManager",
            List(
                sql"""
                CREATE TABLE CivBeacons (
                    ID UUID NOT NULL,
                    World UUID NOT NULL,
                    CoveredArea GEOMETRY(Polygon),
                    GroupID UUID,
                    PRIMARY KEY(ID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates (ID)
                );
                """.command,
                sql"""
                CREATE TABLE Hearts (
                    X BIGINT NOT NULL,
                    Y BIGINT NOT NULL,
                    Z BIGINT NOT NULL,
                    Owner UUID NOT NULL,
                    Beacon UUID NOT NULL,
                    UNIQUE(Owner),
                    FOREIGN KEY (Beacon) REFERENCES CivBeacons (ID)
                );
                """.command,
            ),
            List(
                sql"""
                DROP TABLE Hearts;
                """.command,
                sql"""
                DROP TABLE CivBeacons;
                """.command,
            ),
        )
    )
    sql.applyMigration(
        Storage.Migration(
            "Polygons have Z-values actually",
            List(
                sql"""
                ALTER TABLE CivBeacons ALTER COLUMN CoveredArea SET DATA TYPE GEOMETRY(PolygonZ);
                """.command
            ),
            List(
                sql"""
                ALTER TABLE CivBeacons ALTER COLUMN CoveredArea SET DATA TYPE GEOMETRY(Polygon);
                """.command
            ),
        )
    )
    sql.applyMigration(
        Storage.Migration(
            "Beacon maturation",
            List(
                sql"""
                ALTER TABLE CivBeacons ADD COLUMN CreatedAt TIMESTAMPTZ;
                """.command,
                sql"""
                UPDATE CivBeacons SET CreatedAt = now();
                """.command,
                sql"""
                ALTER TABLE CivBeacons ALTER COLUMN CreatedAt SET NOT NULL;
                """.command,
            ),
            List(
                sql"""
                ALTER TABLE CivBeacons DROP COLUMN CreatedAt;
                """.command
            ),
        )
    )

    private case class WorldData(
        var beaconRTree: RTree[(Polygon, BeaconID)],
        var beaconIDsToRTreeEntries: Map[BeaconID, IndexedSeq[
            RTreeEntry[(Polygon, BeaconID)]
        ]],
    )

    private val geometryFactory = GeometryFactory()
    private var worldData = Map[UUID, WorldData]()
    private val polygonGeojsonCodec = text.imap[Polygon] { json =>
        GeoJsonReader(geometryFactory).read(json).asInstanceOf[Polygon]
    } { polygon =>
        GeoJsonWriter().write(polygon)
    }

    private def getWorldData(w: World)(using Session[IO]): IO[WorldData] =
        if !worldData.contains(w.getUID) then
            for {
                rtree <- loadHearts(w.getUID)
            } yield {
                val entries = rtree.entries.groupMap(_.value._2)(identity)
                worldData += w.getUID -> WorldData(rtree, entries)
                worldData(w.getUID)
            }
        else IO.pure(worldData(w.getUID))

    def getBeaconFor(player: OwnerID)(using Session[IO]): IO[Option[BeaconID]] =
        sql.queryOptionIO(
            sql"""
        SELECT Beacon FROM Hearts WHERE Owner = $uuid;
        """,
            uuid,
            player,
        )

    def getBeaconLocationFor(
        player: OwnerID
    )(using Session[IO]): IO[Option[(Long, Long, Long)]] =
        sql.queryOptionIO(
            sql"""
        SELECT X, Y, Z FROM Hearts WHERE Owner = $uuid;
        """,
            int8 *: int8 *: int8,
            player,
        )

    def setGroup(beacon: BeaconID, group: GroupID)(using
        Session[IO]
    ): IO[Either[Unit, Unit]] =
        for {
            existingGroup <- getGroup(beacon)
            size <- beaconSize(beacon)
            result <-
                if size != 1 && existingGroup.isDefined then IO.pure(Left(()))
                else
                    sql
                        .commandIO(
                            sql"""
                UPDATE CivBeacons SET GroupID = $uuid WHERE ID = $uuid;
                """,
                            (group, beacon),
                        )
                        .map(_ => Right(()))
        } yield result

    def getGroup(beacon: BeaconID)(using Session[IO]): IO[Option[GroupID]] =
        sql.queryOptionIO(
            sql"""
        SELECT GroupID FROM CivBeacons WHERE ID = $uuid AND GroupID IS NOT NULL;
        """,
            uuid,
            beacon,
        )

    private def triangulate(
        id: BeaconID,
        polygon: Polygon,
    ): IndexedSeq[RTreeEntry[(Polygon, BeaconID)]] =
        val triangulator = DelaunayTriangulationBuilder()
        triangulator.setSites(polygon)
        val triangleCollection = triangulator
            .getTriangles(geometryFactory)
            .asInstanceOf[GeometryCollection]
        val triangles =
            for i <- 0 until triangleCollection.getNumGeometries
            yield triangleCollection.getGeometryN(i).asInstanceOf[Polygon]

        triangles.map { triangle =>
            val Array(minmin, _, maxmax, _, _) =
                triangle.getEnvelope
                    .asInstanceOf[Polygon]
                    .getCoordinates

            entry(
                minmin.getX.toFloat,
                minmin.getY.toFloat,
                maxmax.getX.toFloat,
                maxmax.getY.toFloat,
                (triangle, id),
            )
        }

    private def recomputeEntryFor(
        id: BeaconID
    )(using Session[IO]): IO[IndexedSeq[RTreeEntry[(Polygon, BeaconID)]]] =
        sql
            .queryOptionIO(
                sql"""
        SELECT
            ST_AsGeoJSON(CoveredArea)
        FROM
            CivBeacons
        WHERE
            ID = $uuid AND CoveredArea IS NOT NULL;
        """,
                polygonGeojsonCodec,
                id,
            )
            .map(_.map(triangulate(id, _)).getOrElse(IndexedSeq()))

    def getPolygonsFor(group: GroupID)(using Session[IO]): IO[List[Polygon]] =
        sql
            .queryListIO(
                sql"""
                SELECT
                    ST_AsGeoJSON(CoveredArea)
                FROM
                    CivBeacons
                WHERE
                    GroupID = $uuid AND CoveredArea IS NOT NULL;
            """,
                polygonGeojsonCodec,
                (group),
            )

    def getPolygonFor(id: BeaconID)(using Session[IO]): IO[Option[Polygon]] =
        sql
            .queryOptionIO(
                sql"""
        SELECT
            ST_AsGeoJSON(CoveredArea)
        FROM
            CivBeacons
        WHERE
            ID = $uuid AND CoveredArea IS NOT NULL;
        """,
                polygonGeojsonCodec,
                id,
            )

    private def loadHearts(
        world: UUID
    )(using Session[IO]): IO[RTree[(Polygon, BeaconID)]] =
        sql
            .queryListIO(
                sql"""
        SELECT
            ID, ST_AsGeoJSON(CoveredArea)
        FROM
            CivBeacons
        WHERE
            World = $uuid;
        """,
                uuid *: polygonGeojsonCodec.opt,
                world,
            )
            .map(
                _.flatMap((it, bytes) => bytes.map(x => (it, x)))
                    .flatMap(triangulate)
            )
            .map(RTree(_))

    private def hasHeart(owner: UUID)(using Session[IO]): IO[Boolean] =
        sql.queryUniqueIO(
            sql"""
        SELECT EXISTS(SELECT 1 FROM Hearts WHERE Owner = $uuid);
        """,
            bool,
            owner,
        )

    def heartAt(l: Location)(using
        Session[IO]
    ): IO[Option[(OwnerID, BeaconID)]] =
        sql.queryOptionIO(
            sql"""
        SELECT
            Owner, Beacon
        FROM
            Hearts
        INNER JOIN
            CivBeacons
        ON CivBeacons.ID = Hearts.Beacon AND CivBeacons.World = $uuid
        WHERE X = $int8
          AND Y = $int8
          AND Z = $int8
        """,
            uuid *: uuid,
            (l.getWorld.getUID, l.getBlockX, l.getBlockY, l.getBlockZ),
        )

    def beaconContaining(l: Location)(using Session[IO]): IO[Option[BeaconID]] =
        val lx = l.getX.toFloat
        val ly = l.getZ.toFloat
        getWorldData(l.getWorld).map { x =>
            x.beaconRTree
                .searchAll(lx, ly)
                .filter { entry =>
                    val (polygon, _) = entry.value
                    polygon.covers(
                        geometryFactory.createPoint(Coordinate(l.getX, l.getZ))
                    )
                }
                .map(_.value._2)
                .headOption
        }

    def beaconsNearby(l: Location)(using
        Session[IO]
    ): IO[List[(Polygon, World, BeaconID)]] =
        val lx = l.getX.toFloat
        val ly = l.getZ.toFloat
        getWorldData(l.getWorld).flatMap { x =>
            x.beaconRTree
                .nearestK(lx, ly, 1000, 32f)
                .map(_.value._2)
                .distinct
                .toList
                .traverse { beaconID =>
                    getPolygonFor(beaconID)
                        .map(_.get)
                        .map(x => (x, l.getWorld, beaconID))
                }
        }

    def sudoSetBeaconPolygon(beacon: BeaconID, world: World, polygon: Polygon)(
        using Session[IO]
    ): IO[Unit] =
        sql
            .commandIO(
                sql"""
                UPDATE CivBeacons
                    SET CoveredArea
                        = ST_GeomFromGeoJSON($polygonGeojsonCodec)
                WHERE ID = $uuid;
                """,
                (polygon, beacon),
            )
            .flatMap(_ => recomputeEntryFor(beacon))
            .flatMap(x => getWorldData(world).map(x -> _))
            .flatMap { (entry, data) =>
                IO {
                    data.beaconRTree = RTree.update(
                        data.beaconRTree,
                        data.beaconIDsToRTreeEntries
                            .get(beacon)
                            .toSeq
                            .flatten,
                        entry,
                    )
                    data.beaconIDsToRTreeEntries += beacon -> entry
                }
            }

    private def findOverlappingBeacons(polygon: Polygon, excluding: BeaconID)(
        using
        Session[IO],
        Transaction[IO],
    ): IO[Either[PolygonAdjustmentError, Unit]] =
        sql.queryListIO(
            sql"""
        SELECT ID, GroupID, ST_AsGeoJSON(CoveredArea) From CivBeacons WHERE ID != $uuid AND ST_INTERSECTS(CivBeacons.CoveredArea, ST_GeomFromGeoJSON($polygonGeojsonCodec));
        """,
            (uuid *: uuid *: polygonGeojsonCodec),
            (excluding, polygon),
        ).flatMap { beacons =>
            if beacons.size == 0 then IO.pure(Right(()))
            else if beacons.size > 1 then
                IO.pure(Left(PolygonAdjustmentError.overlapsMultiplePolygons()))
            else
                val (id, group, otherPolygon) = beacons(0)
                val intersected =
                    otherPolygon.buffer(0).intersection(polygon.buffer(0))
                val asPolygon =
                    if intersected.isInstanceOf[Polygon] then
                        Some(intersected.asInstanceOf[Polygon])
                    else None
                summon[GroupManager].getGroup(group).value.map { it =>
                    Left(
                        PolygonAdjustmentError
                            .overlapsOneOtherPolygon(
                                id,
                                group,
                                it.toOption.get.metadata.name,
                                asPolygon,
                            )
                    )
                }
        }

    private def validatePolygonWithRegardToHearts(
        beacon: BeaconID,
        world: World,
        polygon: Polygon,
    )(using
        Session[IO]
    ): IO[Either[PolygonAdjustmentError, Unit]] =
        sql
            .queryListIO(
                sql"""
        SELECT
            X, Y, Z
        FROM
            Hearts
        INNER JOIN
            CivBeacons
        ON CivBeacons.ID = Hearts.Beacon AND CivBeacons.World = $uuid
        WHERE
            CivBeacons.ID = $uuid
        """,
                int8 *: int8 *: int8,
                (world.getUID, beacon),
            )
            .map { hearts =>
                val expectedArea =
                    CivBeaconManager.populationToArea(hearts.length)
                val actualArea =
                    polygon.getArea
                val heartsOutsidePolygon =
                    hearts.filterNot((x, y, z) =>
                        polygon.covers(
                            geometryFactory
                                .createPoint(
                                    Coordinate(
                                        x.toDouble,
                                        z.toDouble,
                                        y.toDouble,
                                    )
                                )
                        )
                    )

                if actualArea > expectedArea then
                    Left(
                        PolygonAdjustmentError
                            .polygonTooLarge(expectedArea, actualArea)
                    )
                else if heartsOutsidePolygon.nonEmpty then
                    Left(
                        PolygonAdjustmentError.heartsNotIncludedInPolygon(
                            heartsOutsidePolygon.map((x, y, z) =>
                                Location(
                                    world,
                                    x.toDouble,
                                    y.toDouble,
                                    z.toDouble,
                                )
                            )
                        )
                    )
                else Right(())
            }

    def updateBeaconPolygon(beacon: BeaconID, world: World, polygon: Polygon)(
        using
        Session[IO],
        Transaction[IO],
    ): IO[Either[PolygonAdjustmentError, Unit]] =
        (for {
            _ <- EitherT(findOverlappingBeacons(polygon, beacon))
            _ <- EitherT(
                validatePolygonWithRegardToHearts(beacon, world, polygon)
            )
            _ <- EitherT.right(sudoSetBeaconPolygon(beacon, world, polygon))
        } yield ()).value

    def beaconCreatedAt(
        beacon: BeaconID
    )(using Session[IO]): IO[Option[OffsetDateTime]] =
        sql.queryOptionIO(
            sql"""
        SELECT CreatedAt FROM CivBeacons WHERE ID = $uuid
        """,
            timestamptz,
            beacon,
        )

    def beaconSize(beacon: BeaconID)(using Session[IO]): IO[Long] =
        sql.queryUniqueIO(
            sql"""
        SELECT COUNT(*) FROM Hearts WHERE Beacon = $uuid
        """,
            int8,
            beacon,
        )

    def placeHeart(l: Location, owner: UUID)(using
        Session[IO]
    ): IO[Either[Unit, (BeaconID, Long)]] =
        val offsets =
            List(
                (1, 0, 0),
                (-1, 0, 0),
                (0, 1, 0),
                (0, -1, 0),
                (0, 0, 1),
                (0, 0, -1),
            )

        hasHeart(owner).flatMap { has =>
            if has then IO.pure(Left(()))
            else
                for {
                    adjacentHearts <- offsets.traverse(offset => {
                        val (x, y, z) = offset
                        heartAt(l.clone().add(x, y, z))
                    })
                    currentDate <- c.nowIO()
                    beaconID <- OptionT
                        .fromOption(
                            adjacentHearts
                                .find(x => x.isDefined)
                                .flatten
                                .map((_, beaconID) => beaconID)
                        )
                        .getOrElseF {
                            val newID = UUID.randomUUID()
                            sql
                                .commandIO(
                                    sql"""
                                INSERT INTO CivBeacons (
                                    ID, World, CreatedAt
                                ) VALUES (
                                    $uuid, $uuid, $timestamptz
                                );
                                """,
                                    (newID, l.getWorld.getUID, currentDate),
                                )
                                .map(_ => newID)
                        }
                    _ <- sql.commandIO(
                        sql"""
                        INSERT INTO Hearts (
                            X, Y, Z, Owner, Beacon
                        ) VALUES (
                            $int8, $int8, $int8, $uuid, $uuid
                        );
                        """,
                        (l.getBlockX, l.getBlockY, l.getBlockZ, owner, beaconID),
                    )
                    size <- beaconSize(beaconID)
                } yield Right(beaconID, size)
        }

    def removeHeart(l: Location, owner: OwnerID)(using
        Session[IO],
        Transaction[IO],
    ): IO[Option[(BeaconID, Long)]] =
        sql
            .queryUniqueIO(
                sql"""
        DELETE FROM Hearts WHERE Owner = $uuid RETURNING Beacon;
        """,
                uuid,
                owner,
            )
            .flatMap { beacon =>
                beaconSize(beacon)
                    .flatMap(x => getWorldData(l.getWorld).map(x -> _))
                    .flatMap { (count, data) =>
                        if count == 0 then
                            data.beaconRTree = RTree.update(
                                data.beaconRTree,
                                data.beaconIDsToRTreeEntries
                                    .get(beacon)
                                    .toSeq
                                    .flatten,
                                None,
                            )
                            data.beaconIDsToRTreeEntries -= beacon
                            for {
                                it <- hooks.get
                                _ <- it.beaconDeletionIncoming(beacon)
                                _ <- sql
                                    .commandIO(
                                        sql"""
                        DELETE FROM CivBeacons WHERE ID = $uuid
                        """,
                                        beacon,
                                    )
                            } yield None
                        else IO.pure(Some(beacon, count))
                    }
            }
