// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

import BallCore.Storage

import org.bukkit.Location
import java.util.UUID

import com.github.plokhotnyuk.rtree2d.core._
import EuclideanPlane._

import scalikejdbc._
import org.locationtech.jts.geom.Polygon
import java.io.ObjectInputStream
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.Coordinate
import org.bukkit.World
import java.io.InputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream
import net.kyori.adventure.text.Component
import java.text.DecimalFormat
import BallCore.Groups.GroupID

type OwnerID = UUID
type HeartID = UUID
type BeaconID = UUID

case class Point(x: Int, y: Int, z: Int)

private def addPoint(l: Point, r: Point): Point =
    Point(l.x + r.x, l.y + r.y, l.z + r.z)

case class CivBeaconInformation(
    players: List[UUID],
    centroid: Point
)

val df =
    val it = new DecimalFormat()
    it.setMaximumFractionDigits(1)
    it

enum PolygonAdjustmentError:
    case polygonTooLarge(maximum: Int, actual: Double)
    case heartsNotIncludedInPolygon(at: List[Location])

    def explain: Component =
        import BallCore.UI.ChatElements._

        this match
            case polygonTooLarge(maximum, actual) =>
                txt"This polygon is too large; the maximum area is ${txt(maximum.toString()).color(Colors.teal)} blocks but the actual area is ${txt(df.format(actual)).color(Colors.teal)}"
            case heartsNotIncludedInPolygon(at) =>
                val locations = at.map { loc =>
                    txt"${txt(df.format(loc.getX())).color(Colors.grellow)}/${txt(df.format(loc.getY())).color(Colors.grellow)}"
                }.mkComponent(txt", ")
                txt"There are hearts not included in this polygon at ${locations}"

class CivBeaconManager()(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial CivBeaconManager",
            List(
                sql"""
                CREATE TABLE CivBeacons (
                    ID TEXT NOT NULL,
                    World TEXT NOT NULL,
                    Polygon BLOB,
                    PRIMARY KEY(ID)
                );
                """,
                sql"""
                CREATE TABLE Hearts (
                    X INT NOT NULL,
                    Y INT NOT NULL,
                    Z INT NOT NULL,
                    Owner TEXT NOT NULL,
                    Beacon TEXT NOT NULL,
                    UNIQUE(Owner),
                    FOREIGN KEY (Beacon) REFERENCES CivBeacons (ID)
                );
                """,
            ),
            List(
                sql"""
                DROP TABLE Hearts;
                """,
                sql"""
                DROP TABLE CivBeacons;
                """,
            ),
        ),
    )
    sql.applyMigration(
        Storage.Migration(
            "CivBeaconManager: Beacons can be affiliated with groups now",
            List(
                sql"""
                ALTER TABLE CivBeacons ADD COLUMN GroupID TEXT;
                """,
            ),
            List(
                sql"""
                """,
            ),
        )
    )

    case class WorldData(
        var beaconRTree: RTree[(Polygon, BeaconID)],
        var beaconIDsToRTreeEntries: Map[BeaconID, IndexedSeq[RTreeEntry[(Polygon, BeaconID)]]]
    )

    private implicit val session: DBSession = sql.session
    private val triangulator = DelaunayTriangulationBuilder()
    private val geometryFactory = GeometryFactory()
    private var worldData = Map[UUID, WorldData]()

    private def getWorldData(w: World): WorldData =
        if !worldData.contains(w.getUID()) then
            val rtree = loadHearts(w.getUID())
            val entries = rtree.entries.groupMap(_.value._2)(identity)
            worldData += w.getUID() -> WorldData(rtree, entries)
        worldData(w.getUID())
    def populationToArea(count: Int): Int =
        (30 * 30) * count
    def getBeaconFor(player: OwnerID): Option[BeaconID] =
        sql"""
        SELECT Beacon FROM Hearts WHERE Owner = ${player};
        """
        .map(rs => UUID.fromString(rs.string("Beacon")))
        .single
        .apply()
    def setGroup(beacon: BeaconID, group: GroupID): Either[Unit, Unit] =
        if beaconSize(beacon) != 1 then
            return Left(())
        sql"""
        UPDATE CivBeacons SET GroupID = ${group} WHERE ID = ${beacon}
        """
        .update
        .apply()
        Right(())
    def getGroup(beacon: BeaconID): Option[GroupID] =
        sql"""
        SELECT GroupID FROM CivBeacons WHERE ID = ${beacon}
        """
        .map(rs => rs.string("GroupID"))
        .single
        .apply()
        .map(UUID.fromString)
    def triangulate(id: BeaconID, polygonBlob: InputStream): IndexedSeq[RTreeEntry[(Polygon, BeaconID)]] =
        val polygon = ObjectInputStream(polygonBlob)
            .readObject()
            .asInstanceOf[Polygon]
        triangulator.setSites(polygon)
        val triangleCollection = triangulator
            .getTriangles(geometryFactory)
            .asInstanceOf[GeometryCollection]
        val triangles =
            for i <- 0 until triangleCollection.getNumGeometries()
                yield triangleCollection.getGeometryN(i).asInstanceOf[Polygon]

        triangles.map { triangle =>
            val Array(minmin, _, maxmax, _, _) =
                triangle.getEnvelope()
                    .asInstanceOf[Polygon]
                    .getCoordinates()

            entry(minmin.getX().toFloat, minmin.getY().toFloat, maxmax.getX().toFloat, maxmax.getY().toFloat, (triangle, id))
        }
    def recomputeEntryFor(id: BeaconID): IndexedSeq[RTreeEntry[(Polygon, BeaconID)]] =
        sql"""
        SELECT
            Polygon
        FROM
            CivBeacons
        WHERE
            ID = ${id}
        """
        .map(rs => rs.binaryStream("Polygon"))
        .single
        .apply()
        .map(triangulate(id, _))
        .getOrElse(IndexedSeq())
    def getPolygonFor(id: BeaconID): Option[Polygon] =
        sql"""
        SELECT
            Polygon
        FROM
            CivBeacons
        WHERE
            ID = ${id}
        """
        .map(rs => rs.binaryStream("Polygon"))
        .single
        .apply()
        .flatMap(x => Option(x))
        .map { bst =>
            ObjectInputStream(bst)
                .readObject()
                .asInstanceOf[Polygon]
        }
    def loadHearts(world: UUID): RTree[(Polygon, BeaconID)] =
        val items = sql"""
        SELECT
            ID, Polygon
        FROM
            CivBeacons
        WHERE
            World = ${world};
        """
            .map(rs => (UUID.fromString(rs.string("ID")), rs.binaryStream("Polygon")))
            .list
            .apply()
            .filterNot( (_, polygonBlob) => polygonBlob == null )
            .flatMap(triangulate)
        RTree(items)
    def hasHeart(owner: UUID): Boolean =
        sql"""SELECT EXISTS( SELECT 1 FROM Hearts WHERE Owner = ${owner} );"""
            .map(rs => rs.int(1))
            .single
            .apply()
            .map(_ > 0)
            .getOrElse(false)
    def heartAt(l: Location): Option[(OwnerID, BeaconID)] =
        sql"""
        SELECT
            *
        FROM
            Hearts
        INNER JOIN
            CivBeacons
        ON CivBeacons.ID = Hearts.Beacon AND CivBeacons.World = ${l.getWorld().getUID()}
        WHERE X = ${l.getBlockX()}
          AND Y = ${l.getBlockY()}
          AND Z = ${l.getBlockZ()}
        """
            .map(rs => (UUID.fromString(rs.string("Owner")), UUID.fromString(rs.string("Beacon"))))
            .single
            .apply()
    def beaconContaining(l: Location): Option[BeaconID] =
        val lx = l.getX().toFloat
        val ly = l.getZ().toFloat
        getWorldData(l.getWorld()).beaconRTree.searchAll(lx, ly)
            .filter { entry =>
                val (polygon, _) = entry.value
                polygon.covers( geometryFactory.createPoint(Coordinate(l.getX(), l.getZ())) )
            }
            .map(_.value._2)
            .headOption
    def updateBeaconPolygon(beacon: BeaconID, world: World, polygon: Polygon): Either[PolygonAdjustmentError, Unit] =
        val hearts =
            sql"""
            SELECT
                X, Y, Z
            FROM
                Hearts
            INNER JOIN
                CivBeacons
            ON CivBeacons.ID = Hearts.Beacon AND CivBeacons.World = ${world.getUID()}
            WHERE
                CivBeacons.ID = ${beacon}
            """
            .map(rs => (rs.int("X"), rs.int("Y"), rs.int("Z")))
            .list
            .apply()

        val expectedArea =
            populationToArea(hearts.length)
        val actualArea =
            polygon.getArea()
        val heartsOutsidePolygon =
            hearts.filterNot((x, _, z) => polygon.contains( geometryFactory.createPoint(Coordinate(x, z)) ))

        if actualArea > expectedArea then
            Left(PolygonAdjustmentError.polygonTooLarge(expectedArea, actualArea))
        else if heartsOutsidePolygon.nonEmpty then
            Left(PolygonAdjustmentError.heartsNotIncludedInPolygon( heartsOutsidePolygon.map((x, y, z) => Location(world, x, y, z)) ))
        else
            val baos = ByteArrayOutputStream()
            val oos = ObjectOutputStream(baos)
            oos.writeObject(polygon)
            val ba = baos.toByteArray()

            sql"""
            UPDATE CivBeacons SET Polygon = ${ba} WHERE ID = ${beacon}
            """
            .update
            .apply()

            val entry = recomputeEntryFor(beacon)
            val data = getWorldData(world)
            data.beaconRTree = RTree.update(data.beaconRTree, data.beaconIDsToRTreeEntries.get(beacon).toSeq.flatten, entry)
            data.beaconIDsToRTreeEntries += beacon -> entry

            Right(())
    def beaconSize(beacon: BeaconID): Int =
        sql"""
        SELECT COUNT(*) FROM Hearts WHERE Beacon = ${beacon}
        """
        .map(rs => rs.int(1))
        .single
        .apply()
        .get
    def placeHeart(l: Location, owner: UUID): Option[(BeaconID, Int)] =
        if hasHeart(owner) then
            return None

        val offsets = List((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
        val beacon = offsets.view.map(offset => {
                val (x, y, z) = offset
                heartAt(l.clone().add(x, y, z))
            })
            .find(x => x.isDefined)
            .flatten
            .map(_._2)
            .getOrElse {
                val newID = UUID.randomUUID()
                sql"""
                INSERT INTO CivBeacons (
                    ID, World
                ) VALUES (
                    ${newID}, ${l.getWorld().getUID()}
                );
                """
                .update
                .apply()
                newID
            }
        sql"""
        INSERT INTO Hearts (
            X, Y, Z, Owner, Beacon
        ) VALUES (
            ${l.getBlockX()}, ${l.getBlockY()}, ${l.getBlockZ()}, ${owner}, ${beacon} 
        );
        """
        .update
        .apply()

        Some(beacon, beaconSize(beacon))
    def removeHeart(l: Location, owner: OwnerID): Option[(BeaconID, Int)] =
        sql"""
        DELETE FROM Hearts WHERE Owner = ${owner} RETURNING Beacon;
        """
            .map(rs => UUID.fromString(rs.string("Beacon")))
            .single
            .apply()
            .flatMap { beacon =>
                val data = getWorldData(l.getWorld())

                val count = beaconSize(beacon)
                if count == 0 then
                    sql"""DELETE FROM CivBeacons WHERE ID = ${beacon}""".update.apply()
                    data.beaconRTree = RTree.update(data.beaconRTree, data.beaconIDsToRTreeEntries.get(beacon).toSeq.flatten, None)
                    data.beaconIDsToRTreeEntries -= beacon
                    None
                else
                    Some(beacon, count)
            }
