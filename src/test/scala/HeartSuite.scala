// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage
import BallCore.Hearts
import org.bukkit.Location
import java.util.UUID
import BallCore.Hearts.HeartNetworkInformation
import BallCore.Hearts.HeartNetworkManager
import be.seeseemelk.mockbukkit.WorldMock

class HeartSuite extends munit.FunSuite {
  test("placing standalone heart") {
    given sql: Storage.SQLManager = Storage.SQLManager(test = Some("hs placing standalone heart"))
    given hn: HeartNetworkManager = HeartNetworkManager()
    val world = WorldMock()
    val ownerID = UUID.randomUUID()
    val res = hn.placeHeart(Location(world, 0, 0, 0), ownerID)
    assert(res.isDefined, res)
    val heartNetworkID = res.get._1

    val res2 = hn.heartAt(Location(world, 0, 0, 0))
    assert(res2.isDefined, res2)

    val rtreeRes1 = hn.heartNetworksContaining(Location(world, 1, 0, 1))
    assertEquals(rtreeRes1.length, 1)
    assertEquals(rtreeRes1(0), heartNetworkID)

    val rtreeRes2 = hn.heartNetworksContaining(Location(world, 40, 0, 40))
    assertEquals(rtreeRes2.length, 0)

    val res3 = hn.removeHeart(Location(world, 0, 0, 0), ownerID)
    assert(res3.isEmpty, res3)

    val rtreeRes3 = hn.heartNetworksContaining(Location(world, 1, 0, 1))
    assertEquals(rtreeRes3.length, 0)
  }
  test("two-heart network") {
    given sql: Storage.SQLManager = Storage.SQLManager(test = Some("hs two-heart network"))
    given hn: HeartNetworkManager = HeartNetworkManager()
    val world = WorldMock()
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()

    val (hid, _) = hn.placeHeart(Location(world, 0, 0, 0), id1).get

    val res2 = hn.heartAt(Location(world, 0, 0, 0))
    assert(res2.isDefined, res2)

    val rtreeRes1 = hn.heartNetworksContaining(Location(world, 1, 0, 1))
    assertEquals(rtreeRes1.length, 1)
    assertEquals(rtreeRes1(0), hid)

    val rtreeRes2 = hn.heartNetworksContaining(Location(world, 40, 0, 40))
    assertEquals(rtreeRes2.length, 0)

    val offsets = List((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    offsets.foreach { offset =>
      val (x, y, z) = offset
      val res2 = hn.placeHeart(Location(world, x, y, z), id2)
      assert(res2.isDefined, res2)
      val (hid2, hni2) = res2.get
      assert(hid == hid2, (hid, hid2))
      assert(hni2 == 2, hni2)

      val rtreeRes3 = hn.heartNetworksContaining(Location(world, 1, 0, 1))
      assertEquals(rtreeRes3.length, 1)
      assertEquals(rtreeRes3(0), hid)

      val rtreeRes4 = hn.heartNetworksContaining(Location(world, 40, 0, 40))
      assertEquals(rtreeRes4.length, 1)
      assertEquals(rtreeRes4(0), hid)

      val rtreeRes5 = hn.heartNetworksContaining(Location(world, 80, 0, 80))
      assertEquals(rtreeRes5.length, 0)

      val res3 = hn.removeHeart(Location(world, x, y, z), id2)
      assert(res3.isDefined, res3)
      val (hid3, hni3) = res3.get
      assert(hid == hid3, (hid, hid3))
      assert(hni3 == 1, hni3)
    }
  }
}
