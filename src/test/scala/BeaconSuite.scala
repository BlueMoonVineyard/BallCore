// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage
import BallCore.Beacons
import org.bukkit.Location
import java.util.UUID
import BallCore.Beacons.CivBeaconManager
import be.seeseemelk.mockbukkit.WorldMock
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import scala.util.chaining._
import BallCore.Beacons.PolygonAdjustmentError

class HeartSuite extends munit.FunSuite {
  test("placing standalone heart") {
    given sql: Storage.SQLManager = Storage.SQLManager(test = Some("hs placing standalone heart"))
    given hn: CivBeaconManager = CivBeaconManager()
    val world = WorldMock()
    val ownerID = UUID.randomUUID()
    val res = hn.placeHeart(Location(world, 0, 0, 0), ownerID)
    assert(res.isDefined, res)
    val heartNetworkID = res.get._1

    val res2 = hn.heartAt(Location(world, 0, 0, 0))
    assert(res2.isDefined, res2)

    val res3 = hn.removeHeart(Location(world, 0, 0, 0), ownerID)
    assert(res3.isEmpty, res3)
  }
  test("polygon shenanigans") {
    given sql: Storage.SQLManager = Storage.SQLManager(test = Some("hs placing standalone heart"))
    given hn: CivBeaconManager = CivBeaconManager()
    val world = WorldMock()
    val ownerID = UUID.randomUUID()
    val (beaconID, count) = hn.placeHeart(Location(world, 0, 0, 0), ownerID).get

    val gf = GeometryFactory()
    val validPolygon = gf.createPolygon(Array(
      Coordinate(-10, -10),
      Coordinate(-10, 10),
      Coordinate(10, 10),
      Coordinate(10, -10),
      Coordinate(-10, -10),
    ))

    assertEquals(hn.updateBeaconPolygon(beaconID, world, validPolygon), Right(()))
    assert(hn.beaconContaining(Location(world, 0, 0, 0)).isDefined)

    val tooBigPolygon = gf.createPolygon(Array(
      Coordinate(-1000, -1000),
      Coordinate(-1000, 1000),
      Coordinate(1000, 1000),
      Coordinate(1000, -1000),
      Coordinate(-1000, -1000),
    ))

    hn.updateBeaconPolygon(beaconID, world, tooBigPolygon).pipe { res =>
      assert(res match
        case Left(PolygonAdjustmentError.polygonTooLarge(_, _)) => true
        case _ => false
      , res)
    }
    assert(hn.beaconContaining(Location(world, 0, 0, 0)).isDefined)

    val polygonNotContainingHeart = gf.createPolygon(Array(
      Coordinate(-30, -30),
      Coordinate(-30, -10),
      Coordinate(-10, -10),
      Coordinate(-10, -30),
      Coordinate(-30, -30),
    ))

    hn.updateBeaconPolygon(beaconID, world, polygonNotContainingHeart).pipe { res =>
      assert(res match
        case Left(PolygonAdjustmentError.heartsNotIncludedInPolygon(_)) => true
        case _ => false
      , res)
    }
    assert(hn.beaconContaining(Location(world, 0, 0, 0)).isDefined)
  }
  test("two-heart beacon") {
    given sql: Storage.SQLManager = Storage.SQLManager(test = Some("hs two-heart beacon"))
    given hn: CivBeaconManager = CivBeaconManager()
    val world = WorldMock()
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()

    val (hid, _) = hn.placeHeart(Location(world, 0, 0, 0), id1).get

    val res2 = hn.heartAt(Location(world, 0, 0, 0))
    assert(res2.isDefined, res2)

    val offsets = List((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    offsets.foreach { offset =>
      val (x, y, z) = offset
      val res2 = hn.placeHeart(Location(world, x, y, z), id2)
      assert(res2.isDefined, res2)
      val (hid2, hni2) = res2.get
      assert(hid == hid2, (hid, hid2))
      assert(hni2 == 2, hni2)

      val res3 = hn.removeHeart(Location(world, x, y, z), id2)
      assert(res3.isDefined, res3)
      val (hid3, hni3) = res3.get
      assert(hid == hid3, (hid, hid3))
      assert(hni3 == 1, hni3)
    }
  }
}
