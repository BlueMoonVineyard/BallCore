// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Hearts

import BallCore.Storage

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

case class Point(x: Int, y: Int, z: Int)

private def addPoint(l: Point, r: Point): Point =
    Point(l.x + r.x, l.y + r.y, l.z + r.z)

case class HeartNetworkInformation(
    players: List[UUID],
    centroid: Point
)

object HeartNetwork:
    def hasHeart(owner: UUID)(using kvs: Storage.KeyVal): Boolean =
        kvs.get[UUID](owner, "HeartNetworkID") match
            case Some(_) => true
            case None => false
    def heartNetworkAt(l: Location)(using kvs: Storage.KeyVal): Option[(UUID, HeartNetworkInformation)] =
        val point = Point(l.getBlockX(), l.getBlockY(), l.getBlockZ())
        kvs.get[UUID]("LocationsToHeartOwners", point.asJson.noSpaces)
            .flatMap(x => kvs.get[UUID](x, "HeartNetworkID"))
            .flatMap(x => kvs.get[HeartNetworkInformation]("HeartNetworks", x.toString()).map(y => (x, y)))
    def placeHeart(l: Location, owner: UUID)(using kvs: Storage.KeyVal): Option[(UUID, HeartNetworkInformation)] =
        kvs.get[UUID](owner, "HeartNetworkID") match
            case Some(_) =>
                None
            case None =>
                val point = Point(l.getBlockX(), l.getBlockY(), l.getBlockZ())
                kvs.set(owner, "HeartLocation", point)
                kvs.set("LocationsToHeartOwners", point.asJson.noSpaces, owner)

                val offsets = List((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
                offsets.view.map(offset => {
                        val (x, y, z) = offset
                        heartNetworkAt(l.clone().add(x, y, z))
                    })
                    .find(x => x.isDefined)
                    .flatten match
                        case Some((id, x)) =>
                            // println("existing heart network")
                            val hni = x.copy(players = x.players.appended(owner))
                            kvs.set(owner, "HeartNetworkID", id)
                            kvs.set("HeartNetworks", id.toString(), hni)
                            Some((id, hni))
                        case None =>
                            // println("new heart network")
                            val newUUID = UUID.randomUUID()
                            val hni = HeartNetworkInformation(List(owner), point)
                            kvs.set(owner, "HeartNetworkID", newUUID)
                            kvs.set("HeartNetworks", newUUID.toString(), hni)
                            Some((newUUID, hni))
    def removeHeart(l: Location, owner: UUID)(using kvs: Storage.KeyVal): Option[(UUID, HeartNetworkInformation)] =
        kvs.get[UUID](owner, "HeartNetworkID") match
            case None =>
                None
            case Some(v) =>
                kvs.remove(owner, "HeartNetworkID")
                kvs.remove(owner, "HeartLocation")

                val info = kvs.get[HeartNetworkInformation]("HeartNetworks", v.toString()).get
                val newPlayers = info.players.filterNot(x => x == owner)
                if newPlayers.length == 0 then
                    kvs.remove("HeartNetworks", v.toString())
                    None
                else
                    val points = newPlayers.map { x => kvs.get[Point](x, "HeartLocation").get }
                    val sum = points.reduceLeft { (s, v) => addPoint(s, v) }
                    val centroid = Point(sum.x / points.size, sum.y / points.size, sum.z / points.size)
                    val hni = info.copy(players = newPlayers, centroid = centroid)
                    kvs.set("HeartNetworks", v.toString(), hni)
                    Some(v, hni)
