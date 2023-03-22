// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Hearts

import BallCore.Storage

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon

import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor

type OwnerID = UUID
type HeartID = UUID
type HeartNetworkID = UUID

case class Point(x: Int, y: Int, z: Int)

private def addPoint(l: Point, r: Point): Point =
    Point(l.x + r.x, l.y + r.y, l.z + r.z)

case class HeartNetworkInformation(
    players: List[UUID],
    centroid: Point
)

class HeartNetworkManager()(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial HeartNetworkManager",
            List(
                sql"""
                CREATE TABLE HeartNetworks (
                    ID TEXT NOT NULL,
                    PRIMARY KEY(ID)
                );
                """,
                sql"""
                CREATE TABLE Hearts (
                    X INT NOT NULL,
                    Y INT NOT NULL,
                    Z INT NOT NULL,
                    World TEXT NOT NULL,
                    Owner TEXT NOT NULL,
                    Network TEXT NOT NULL,
                    UNIQUE(Owner),
                    FOREIGN KEY (Network) REFERENCES HeartNetworks (ID)
                );
                """,
            ),
            List(
                sql"""
                DROP TABLE Hearts;
                """,
                sql"""
                DROP TABLE HeartNetworks;
                """,
            ),
        )
    )
    private implicit val session: DBSession = AutoSession

    def hasHeart(owner: UUID): Boolean =
        sql"""SELECT EXISTS( SELECT 1 FROM Hearts WHERE Owner = ${owner} );"""
            .map(rs => rs.int(1))
            .single
            .apply()
            .map(_ > 0)
            .getOrElse(false)
    def heartAt(l: Location): Option[(OwnerID, HeartNetworkID)] =
        sql"""SELECT * FROM HEARTS WHERE X = ${l.getBlockX()} AND Y = ${l.getBlockY()} AND Z = ${l.getBlockZ()}"""
            .map(rs => (UUID.fromString(rs.string("Owner")), UUID.fromString(rs.string("Network"))))
            .single
            .apply()
    def heartNetworkSize(network: HeartNetworkID): Int =
        sql"""
        SELECT COUNT(*) FROM Hearts WHERE Network = ${network}
        """
        .map(rs => rs.int(1))
        .single
        .apply()
        .get
    def placeHeart(l: Location, owner: UUID): Option[(HeartNetworkID, Int)] =
        if hasHeart(owner) then
            return None

        val offsets = List((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
        val network = offsets.view.map(offset => {
                val (x, y, z) = offset
                heartAt(l.clone().add(x, y, z))
            })
            .find(x => x.isDefined)
            .flatten
            .map(_._2)
            .getOrElse {
                val newID = UUID.randomUUID()
                sql"""
                INSERT INTO HeartNetworks (
                    ID
                ) VALUES (
                    ${newID}
                );
                """
                .update
                .apply()
                newID
            }
        sql"""
        INSERT INTO Hearts (
            X, Y, Z, World, Owner, Network
        ) VALUES (
            ${l.getBlockX()}, ${l.getBlockY()}, ${l.getBlockZ()}, ${l.getWorld().getUID()}, ${owner}, ${network} 
        );
        """
        .update
        .apply()

        Some(network, heartNetworkSize(network))
    def removeHeart(l: Location, owner: OwnerID): Option[(HeartNetworkID, Int)] =
        sql"""
        DELETE FROM Hearts WHERE Owner = ${owner} RETURNING Network;
        """
            .map(rs => UUID.fromString(rs.string("Network")))
            .single
            .apply()
            .flatMap { network =>
                val count = heartNetworkSize(network)
                if count == 0 then
                    sql"""DELETE FROM HeartNetworks WHERE ID = ${network}""".update.apply()
                    None
                else
                    Some(network, count)
            }
