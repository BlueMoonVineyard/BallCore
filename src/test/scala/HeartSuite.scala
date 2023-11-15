// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Beacons.{CivBeaconManager, PolygonAdjustmentError}
import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager
import BallCore.{Beacons, Storage}
import be.seeseemelk.mockbukkit.WorldMock
import org.bukkit.Location
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}

import java.util.UUID
import scala.util.chaining.*

class HeartSuite extends munit.FunSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    sql.test("placing standalone heart") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given hn: CivBeaconManager = CivBeaconManager()
        val world = WorldMock()
        val ownerID = UUID.randomUUID()
        val res =
            sql.useBlocking(hn.placeHeart(Location(world, 0, 0, 0), ownerID))
        assert(res.isRight, res)

        val res2 = sql.useBlocking(hn.heartAt(Location(world, 0, 0, 0)))
        assert(res2.isDefined, res2)

        val res3 =
            sql.useBlocking(hn.removeHeart(Location(world, 0, 0, 0), ownerID))
        assert(res3.isEmpty, res3)
    }
    sql.test("polygon shenanigans") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given hn: CivBeaconManager = CivBeaconManager()
        val world = WorldMock()
        val ownerID = UUID.randomUUID()
        val (beaconID, count) = sql
            .useBlocking(hn.placeHeart(Location(world, 0, 0, 0), ownerID))
            .toOption
            .get

        val gf = GeometryFactory()
        val validPolygon = gf.createPolygon(
            Array(
                Coordinate(-10, -10, 0),
                Coordinate(-10, 10, 0),
                Coordinate(10, 10, 0),
                Coordinate(10, -10, 0),
                Coordinate(-10, -10, 0),
            )
        )

        assertEquals(
            sql.useBlocking(
                hn.updateBeaconPolygon(beaconID, world, validPolygon)
            ),
            Right(()),
        )
        assert(
            sql.useBlocking(hn.beaconContaining(Location(world, 0, 0, 0)))
                .isDefined
        )

        val tooBigPolygon = gf.createPolygon(
            Array(
                Coordinate(-1000, -1000, 0),
                Coordinate(-1000, 1000, 0),
                Coordinate(1000, 1000, 0),
                Coordinate(1000, -1000, 0),
                Coordinate(-1000, -1000, 0),
            )
        )

        sql
            .useBlocking(hn.updateBeaconPolygon(beaconID, world, tooBigPolygon))
            .pipe { res =>
                assert(
                    res match
                        case Left(
                                PolygonAdjustmentError.polygonTooLarge(_, _)
                            ) =>
                            true
                        case _ => false
                    ,
                    res,
                )
            }
        assert(
            sql.useBlocking(hn.beaconContaining(Location(world, 0, 0, 0)))
                .isDefined
        )

        val polygonNotContainingHeart = gf.createPolygon(
            Array(
                Coordinate(-30, -30, 0),
                Coordinate(-30, -10, 0),
                Coordinate(-10, -10, 0),
                Coordinate(-10, -30, 0),
                Coordinate(-30, -30, 0),
            )
        )

        sql
            .useBlocking(
                hn.updateBeaconPolygon(
                    beaconID,
                    world,
                    polygonNotContainingHeart,
                )
            )
            .pipe { res =>
                assert(
                    res match
                        case Left(
                                PolygonAdjustmentError
                                    .heartsNotIncludedInPolygon(_)
                            ) =>
                            true
                        case _ => false
                    ,
                    res,
                )
            }
        assert(
            sql.useBlocking(hn.beaconContaining(Location(world, 0, 0, 0)))
                .isDefined
        )
    }
    sql.test("two-heart beacon") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given hn: CivBeaconManager = CivBeaconManager()
        val world = WorldMock()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        val (hid, _) =
            sql.useBlocking(hn.placeHeart(Location(world, 0, 0, 0), id1))
                .toOption
                .get

        val res2 = sql.useBlocking(hn.heartAt(Location(world, 0, 0, 0)))
        assert(res2.isDefined, res2)

        val offsets =
            List(
                (1, 0, 0),
                (-1, 0, 0),
                (0, 1, 0),
                (0, -1, 0),
                (0, 0, 1),
                (0, 0, -1),
            )
        offsets.foreach { offset =>
            val (x, y, z) = offset
            val res2 =
                sql.useBlocking(hn.placeHeart(Location(world, x, y, z), id2))
            assert(res2.isRight, res2)
            val (hid2, hni2) = res2.toOption.get
            assert(hid == hid2, (hid, hid2))
            assert(hni2 == 2, hni2)

            val res3 =
                sql.useBlocking(hn.removeHeart(Location(world, x, y, z), id2))
            assert(res3.isDefined, res3)
            val (hid3, hni3) = res3.get
            assert(hid == hid3, (hid, hid3))
            assert(hni3 == 1, hni3)
        }
    }
    sql.test("multiple claim areas") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given hn: CivBeaconManager = CivBeaconManager()
        val world = WorldMock()
        val id1 = UUID.randomUUID()
        val (hid1, _) =
            sql.useBlocking(hn.placeHeart(Location(world, 0, -10, 0), id1))
                .toOption
                .get

        val id2 = UUID.randomUUID()
        val (hid2, _) =
            sql.useBlocking(hn.placeHeart(Location(world, 100, 10, 100), id2))
                .toOption
                .get

        assertNotEquals(hid1, hid2, "different heart IDs")

        val gf = GeometryFactory()
        val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
        assert(area1.covers(gf.createPoint(Coordinate(0, 0, -10))), "sanity check of area 1")
        val res1 = sql.useBlocking(hn.updateBeaconPolygon(hid1, world, area1))
        assert(res1.isRight, (res1, "setting first heart's area"))

        val area2 = rectangleCenteredAt(gf, 100, 100, 10, 5)
        assert(area2.covers(gf.createPoint(Coordinate(100, 100, 10))), "sanity check of area 2")
        val res2 = sql.useBlocking(hn.updateBeaconPolygon(hid2, world, area2))
        assert(res2.isRight, (res2, "setting second heart's area"))

        val covered1 = sql.useBlocking(hn.beaconContaining(Location(world, 1, -10, 1)))
        assertEquals(covered1, Some(hid1), "area 1 contains beacon 1")

        val covered2 = sql.useBlocking(hn.beaconContaining(Location(world, 101, 10, 101)))
        assertEquals(covered2, Some(hid2), "area 2 contains beacon 2")
    }
}
