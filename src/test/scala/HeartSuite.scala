import BallCore.Storage
import BallCore.Hearts
import BallCore.Hearts.HeartNetwork
import org.bukkit.Location
import java.util.UUID
import BallCore.Hearts.HeartNetworkInformation

class HeartSuite extends munit.FunSuite {
  test("placing standalone heart") {
    given kv: Storage.MemKeyVal = Storage.MemKeyVal()
    val id = UUID.randomUUID()
    val res = HeartNetwork.placeHeart(Location(null, 0, 0, 0), id)
    assert(res.isDefined, res)

    val res2 = HeartNetwork.heartNetworkAt(Location(null, 0, 0, 0))
    assert(res2.isDefined, res2)

    val res3 = HeartNetwork.removeHeart(Location(null, 0, 0, 0), id)
    assert(res3.isEmpty, res3)
  }
  test("two-heart network") {
    given kv: Storage.MemKeyVal = Storage.MemKeyVal()
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()

    val (hid, _) = HeartNetwork.placeHeart(Location(null, 0, 0, 0), id1).get

    val res2 = HeartNetwork.heartNetworkAt(Location(null, 0, 0, 0))
    assert(res2.isDefined, res2)

    val offsets = List((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    offsets.foreach { offset =>
      val (x, y, z) = offset
      val res2 = HeartNetwork.placeHeart(Location(null, x, y, z), id2)
      assert(res2.isDefined, res2)
      val (hid2, hni2) = res2.get
      assert(hid == hid2, (hid, hid2))
      assert(hni2.players.size == 2, hni2)

      val res3 = HeartNetwork.removeHeart(Location(null, x, y, z), id2)
      assert(res3.isDefined, res3)
      val (hid3, hni3) = res3.get
      assert(hid == hid3, (hid, hid3))
      assert(hni3.players.size == 1, hni3)
    }
  }
}
