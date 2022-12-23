// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Storage
import BallCore.DataStructures.LRUCache

import java.{util => ju}
import scala.collection.mutable.Map

import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor

/** The ChunkStateManager is responsible for managing the loading and saving of chunkstates to a SQL database */
class ChunkStateManager()(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial ChunkStateManager",
            List(
                sql"""
                CREATE TABLE Reinforcements (
                    ChunkX INTEGER NOT NULL,
                    ChunkZ INTEGER NOT NULL,
                    World TEXT NOT NULL,
                    OffsetX INTEGER NOT NULL,
                    OffsetZ INTEGER NOT NULL,
                    Y INTEGER NOT NULL,
                    Group TEXT NOT NULL,
                    Owner TEXT NOT NULL,
                );
                """
            ),
            List(
                sql"""
                DROP TABLE Reinforcements;
                """
            ),
        )
    )

    private implicit val session: DBSession = AutoSession
    private val cache = LRUCache[ChunkKey, ChunkState](1000, evict)

    private def evict(key: ChunkKey, value: ChunkState): Unit =
        set(key, value)

    private def load(key: ChunkKey): Unit =
        val cs = ChunkState(Map())
        val items =
            sql"""
            SELECT (OffsetX, OffsetZ, Y, Group, Owner) FROM ChunkStateManager
                WHERE ChunkX = ${key.chunkX}
                  AND ChunkZ = ${key.chunkZ}
                  AND World = ${key.world};
            """
                .map(rs => (rs.int("OffsetX"), rs.int("OffsetZ"), rs.int("Y"), rs.string("Group"), rs.string("Owner")))
                .list
                .apply()
                .foreach { tuple =>
                    val (offsetX, offsetZ, y, group, owner) = tuple
                    val gid = ju.UUID.fromString(group)
                    val uid = ju.UUID.fromString(owner)
                    cs.blocks(BlockKey(offsetX, offsetZ, y)) = BlockState(gid, uid, false, false)
                }
        cache(key) = cs

    def get(key: ChunkKey): ChunkState =
        if !cache.contains(key) then
            load(key)
        if !cache.contains(key) then
            cache(key) = ChunkState(Map())
        cache(key)
        

    def set(key: ChunkKey, value: ChunkState): Unit =
        val cx = key.chunkX
        val cz = key.chunkZ
        val cw = key.world

        DB.localTx { implicit session =>
            value.blocks.view.filter(_._2.deleted).foreach { item =>
                val (key, _) = item
                sql"""
                DELETE FROM Reinforcements
                    WHERE ChunkX = ${cx}
                      AND ChunkZ = ${cz}
                      AND World = ${cw}
                      AND OffsetX = ${key.offsetX}
                      AND OffsetZ = ${key.offsetZ}
                      AND Y = ${key.y};
                """.update.apply()
            }
            value.blocks.view.filter(item => item._2.dirty && !item._2.deleted).foreach { item =>
                val (key, value) = item
                sql"""
                INSERT OR REPLACE INTO Reinforcements
                    (ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y, Group, Owner)
                    VALUES
                    (${cx},  ${cz},  ${cw}, ${key.offsetX}, ${key.offsetZ}, ${key.y}, ${value.group}, ${value.owner})
                """
            }
        }

        value.blocks.filterInPlace( (key, value) => !value.deleted )
        value.blocks.mapValuesInPlace ( (key, value) =>
            if value.dirty then
                value.copy(dirty = false)
            else
                value
        )