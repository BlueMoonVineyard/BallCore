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
    val id = UUID.randomUUID()
    val res = hn.placeHeart(Location(world, 0, 0, 0), id)
    assert(res.isDefined, res)

    val res2 = hn.heartAt(Location(world, 0, 0, 0))
    assert(res2.isDefined, res2)

    val res3 = hn.removeHeart(Location(world, 0, 0, 0), id)
    assert(res3.isEmpty, res3)
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
