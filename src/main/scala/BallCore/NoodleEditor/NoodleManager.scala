package BallCore.NoodleEditor

import BallCore.Storage.SQLManager
import BallCore.Storage.Migration
import skunk.implicits._
import skunk.codec.all._
import skunk.Session
import cats.effect.kernel.Resource
import org.locationtech.jts.geom.MultiLineString
import BallCore.Groups.SubgroupID
import BallCore.Groups.GroupID
import com.github.plokhotnyuk.rtree2d.core.RTree
import com.github.plokhotnyuk.rtree2d.core.RTreeEntry
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import cats.effect.IO
import java.util.UUID
import scala.collection.concurrent.TrieMap
import org.bukkit.Location
import org.locationtech.jts.geom.Coordinate
import skunk.Transaction
import BallCore.Groups.UserID
import cats.data.EitherT
import BallCore.Reinforcements.WorldID
import BallCore.Groups.nullUUID
import BallCore.Groups.GroupError
import BallCore.Groups.GroupManager
import BallCore.Groups.Permissions

val NoodleSize = 4

case class NoodleKey(group: GroupID, subgroup: SubgroupID)

trait NoodleVerifier[Error]:
    def verify(user: UserID, group: NoodleKey)(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[Error, Unit]]

class NoodleManager()(using sql: SQLManager, gm: GroupManager):
    sql.applyMigration(
        Migration(
            "Initial Noodle Manager",
            List(
                sql"""
                CREATE TABLE Noodles (
                    Area GEOMETRY(MultiLineStringZ) NOT NULL,
                    GroupID UUID NOT NULL REFERENCES GroupStates(ID) ON DELETE CASCADE,
                    SubgroupID UUID REFERENCES Subgroups(SubgroupID) ON DELETE CASCADE,
                    WorldID UUID NOT NULL,
                    UNIQUE(GroupID, SubgroupID, WorldID)
                )
                """.command
            ),
            List(
                sql"""
                DROP TABLE Noodles;
                """.command
            ),
        )
    )

    private def verify(user: UserID, group: NoodleKey)(using Session[IO], Transaction[IO]): IO[Either[GroupError, Unit]] =
        gm.checkE(user, group.group, group.subgroup, Permissions.ManageClaims).value

    private case class WorldData(
        var noodleRTree: RTree[(Geometry, NoodleKey)],
        var noodleKeysToRTreeEntries: Map[NoodleKey, IndexedSeq[
            RTreeEntry[(Geometry, NoodleKey)],
        ]],
    )
    private val geometryFactory = GeometryFactory()
    private def triangulate(
        id: NoodleKey,
        noodle: MultiLineString,
    ): IndexedSeq[RTreeEntry[(Geometry, NoodleKey)]] =
        import com.github.plokhotnyuk.rtree2d.core.*
        import com.github.plokhotnyuk.rtree2d.core.EuclideanPlane.*
        import org.locationtech.jts.operation.buffer.BufferParameters
        import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
        import org.locationtech.jts.geom.GeometryCollection
        import org.locationtech.jts.geom.Polygon

        val polygon = noodle.buffer(NoodleSize, 2, BufferParameters.CAP_FLAT)
        polygon.apply { (coord: Coordinate) =>
            val closestNoodle = noodle.getCoordinates().sortWith(_.distance(coord) < _.distance(coord)).head
            coord.setZ(closestNoodle.getZ())
        }
        polygon.geometryChanged()

        val triangulator = DelaunayTriangulationBuilder()
        triangulator.setSites(polygon)
        val triangleCollection = triangulator
            .getTriangles(geometryFactory)
            .asInstanceOf[GeometryCollection]
        val triangles =
            for i <- 0 until triangleCollection.getNumGeometries
            yield triangleCollection.getGeometryN(i)

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
    private val multiLineStringCodec =
        import org.locationtech.jts.io.geojson.{GeoJsonReader, GeoJsonWriter}

        text.imap[MultiLineString] { json =>
            GeoJsonReader(geometryFactory)
                .read(json)
                .asInstanceOf[MultiLineString]
        } { polygon =>
            GeoJsonWriter().write(polygon)
        }
    private val subgroupCodec =
        uuid.opt.imap { optional =>
            optional.getOrElse(nullUUID)
        } { id =>
            Some(id).filterNot(_ == nullUUID)
        }
    private def loadNoodles(
        world: UUID
    )(using Session[IO]): IO[RTree[(Geometry, NoodleKey)]] =
        sql
            .queryListIO(
                sql"""
        SELECT
            GroupID, SubgroupID, ST_AsGeoJSON(Area)
        FROM
            Noodles
        WHERE
            WorldID = $uuid;
        """,
                (uuid *: subgroupCodec).to[NoodleKey] *: multiLineStringCodec,
                world,
            )
            .map(
                _.flatMap(triangulate)
            )
            .map(RTree(_))

    private val worldData = TrieMap[UUID, WorldData]()
    private def getWorldData(
        world: UUID
    )(using rsrc: Resource[IO, Session[IO]]): IO[WorldData] =
        if !worldData.contains(world) then
            for {
                rtree <- rsrc.use { implicit session => loadNoodles(world) }
            } yield {
                val entries = rtree.entries.groupMap(_.value._2)(identity)
                worldData += world -> WorldData(rtree, entries)
                worldData(world)
            }
        else IO.pure(worldData(world))

    private def interpolateZ(geo: Geometry, coord: Coordinate): Double =
        import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
        import org.locationtech.jts.triangulate.quadedge.Vertex
        val triangulator = DelaunayTriangulationBuilder()
        triangulator.setSites(geo)
        val edge = triangulator.getSubdivision().locate(coord)
        Vertex(coord.x, coord.y)
            .interpolateZValue(edge.orig(), edge.dest(), edge.oPrev().dest())

    def noodleAt(l: Location)(using
        Resource[IO, Session[IO]]
    ): IO[Option[NoodleKey]] =
        val lx = l.getX.toFloat
        val ly = l.getZ.toFloat
        getWorldData(l.getWorld.getUID).map { worldData =>
            worldData.noodleRTree
                .searchAll(lx, ly)
                // filter in 2D
                .filter { entry =>
                    val (polygon, _) = entry.value
                    val coord = Coordinate(l.getX, l.getZ)
                    val covers = polygon.covers(
                        geometryFactory.createPoint(coord)
                    )
                    covers
                }
                // filter in 3D
                .filter { entry =>
                    val (polygon, _) = entry.value
                    val coord = Coordinate(l.getX, l.getZ)
                    val zOnPolygon = interpolateZ(polygon, coord)
                    val dZ = zOnPolygon - l.getY
                    dZ.abs <= NoodleSize
                }
                .map(_.value._2)
                .headOption
        }
    def getNoodleFor(key: NoodleKey, world: WorldID)(using
        Session[IO]
    ): IO[Option[MultiLineString]] =
        sql.queryOptionIO(
            sql"""
        SELECT ST_AsGeoJSON(Area) FROM Noodles
            WHERE GroupID = $uuid
            AND SubgroupID = $subgroupCodec
            AND WorldID = $uuid;
        """,
            multiLineStringCodec,
            (key.group, key.subgroup, world),
        )
    def setNoodleFor(
        as: UserID,
        key: NoodleKey,
        multiLineString: MultiLineString,
        world: WorldID,
    )(using Session[IO], Transaction[IO]): IO[Either[GroupError, Unit]] =
        (for {
            _ <- EitherT(verify(as, key))
            _ <- EitherT.right(
                sql.commandIO(
                    sql"""
            INSERT INTO Noodles (
                GroupID, SubgroupID, WorldID, Area
            ) VALUES (
                $uuid, $subgroupCodec, $uuid, ST_GeomFromGeoJSON($multiLineStringCodec)
            ) ON CONFLICT (GroupID, SubgroupID, WorldID) DO UPDATE SET Area = EXCLUDED.Area;
            """,
                    (key.group, key.subgroup, world, multiLineString),
                )
            )
            newEntries = triangulate(key, multiLineString)
            worldData <- EitherT.right(
                getWorldData(world)(using Resource.pure(summon[Session[IO]]))
            )
            _ <- EitherT.right(IO {
                worldData.noodleRTree = RTree.update(
                    worldData.noodleRTree,
                    worldData.noodleKeysToRTreeEntries
                        .get(key)
                        .toSeq
                        .flatten,
                    newEntries,
                )
                worldData.noodleKeysToRTreeEntries += key -> newEntries
            })
        } yield ()).value
