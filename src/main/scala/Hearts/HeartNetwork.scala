package BallCore.Hearts

import BallCore.Storage

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon

case class Point(x: Int, y: Int, z: Int)

private def addPoint(l: Point, r: Point): Point =
    Point(l.x + r.x, l.y + r.y, l.z + r.z)

case class HeartNetworkInformation(
    players: List[UUID],
    centroid: Point
)

object HeartNetwork:
    def hasHeart(owner: Player)(using kvs: Storage.KeyVal): Boolean =
        kvs.get[UUID](owner.getUniqueId(), "HeartNetworkID") match
            case Some(_) => true
            case None => false
    def placeHeart(l: Location, owner: Player)(using kvs: Storage.KeyVal, sf: SlimefunAddon): Option[HeartNetworkInformation] =
        kvs.get[UUID](owner.getUniqueId(), "HeartNetworkID") match
            case Some(_) =>
                None
            case None =>
                // TODO: joining to existing networks
                val newUUID = UUID.randomUUID()
                val centroid = Point(l.getBlockX(), l.getBlockY(), l.getBlockZ())
                kvs.set(owner.getUniqueId(), "HeartLocation", centroid)
                kvs.set(owner.getUniqueId(), "HeartNetworkID", newUUID)
                val hni = HeartNetworkInformation(List(owner.getUniqueId()), centroid)
                kvs.set("HeartNetworks", newUUID.toString(), hni)
                Some(hni)
    def removeHeart(l: Location, owner: Player)(using kvs: Storage.KeyVal): Option[HeartNetworkInformation] =
        kvs.get[UUID](owner.getUniqueId(), "HeartNetworkID") match
            case None =>
                None
            case Some(v) =>
                kvs.remove(owner.getUniqueId(), "HeartNetworkID")
                kvs.remove(owner.getUniqueId(), "HeartLocation")

                val info = kvs.get[HeartNetworkInformation]("HeartNetworks", v.toString()).get
                val newPlayers = info.players.filterNot(x => x == owner.getUniqueId())
                if newPlayers.length == 0 then
                    kvs.remove("HeartNetworks", v.toString())
                    None
                else
                    val points = newPlayers.map { x => kvs.get[Point](x, "HeartLocation").get }
                    val sum = points.reduceLeft { (s, v) => addPoint(s, v) }
                    val centroid = Point(sum.x / points.size, sum.y / points.size, sum.z / points.size)
                    val hni = info.copy(players = newPlayers, centroid = centroid)
                    kvs.set("HeartNetworks", v.toString(), hni)
                    Some(hni)
