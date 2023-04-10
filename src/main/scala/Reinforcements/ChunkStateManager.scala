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
                    GroupID TEXT NOT NULL,
                    Owner TEXT NOT NULL,
                    Health INTEGER NOT NULL,
                    ReinforcementKind TEXT NOT NULL,
                    PlacedAt TEXT NOT NULL
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
    sql.applyMigration(
        Storage.Migration(
            "Split Reinforcements from blocks",
            List(
                sql"""
                ALTER TABLE Reinforcements ADD COLUMN ID TEXT NOT NULL DEFAULT (generateUUID());
                """.tags("returns"),
                sql"""
                CREATE UNIQUE INDEX reinforcements_unique_id ON Reinforcements(ID);
                """,
                sql"""
                CREATE TABLE BlockReinforcements (
                    ChunkX INTEGER NOT NULL,
                    ChunkZ INTEGER NOT NULL,
                    World TEXT NOT NULL,
                    OffsetX INTEGER NOT NULL,
                    OffsetZ INTEGER NOT NULL,
                    Y INTEGER NOT NULL,
                    ReinforcementID TEXT NOT NULL,
                    UNIQUE(ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y),
                    FOREIGN KEY (ReinforcementID) REFERENCES Reinforcements(ID) ON DELETE CASCADE
                );
                """,
                sql"""
                INSERT INTO BlockReinforcements SELECT ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y, ID as ReinforcementID FROM Reinforcements;
                """,
                sql"ALTER TABLE Reinforcements DROP COLUMN ChunkX;",
                sql"ALTER TABLE Reinforcements DROP COLUMN ChunkZ;",
                sql"ALTER TABLE Reinforcements DROP COLUMN World;",
                sql"ALTER TABLE Reinforcements DROP COLUMN OffsetX;",
                sql"ALTER TABLE Reinforcements DROP COLUMN OffsetZ;",
                sql"ALTER TABLE Reinforcements DROP COLUMN Y;",
            ),
            List(
                sql"""
                """,
            ),
        )
    )

    private implicit val session: DBSession = AutoSession
    private val cache = LRUCache[ChunkKey, ChunkState](1000, evict)

    def evictAll(): Unit =
        cache.foreach { (x, y) => set(x, y) }
        cache.clear()

    private def evict(key: ChunkKey, value: ChunkState): Unit =
        set(key, value)

    private def load(key: ChunkKey): Unit =
        val cs = ChunkState(Map())
        val items =
            sql"""
            SELECT OffsetX, OffsetZ, Y, GroupID, Owner, Health, ReinforcementKind, PlacedAt FROM BlockReinforcements
                LEFT JOIN Reinforcements
                       ON Reinforcements.ID = BlockReinforcements.ReinforcementID

                WHERE ChunkX = ${key.chunkX}
                  AND ChunkZ = ${key.chunkZ}
                  AND World = ${key.world};
            """
                .map(rs => (rs.int("OffsetX"), rs.int("OffsetZ"), rs.int("Y"), rs.string("GroupID"), rs.string("Owner"), rs.int("Health"), rs.string("ReinforcementKind"), rs.date("PlacedAt")))
                .list
                .apply()
                .foreach { tuple =>
                    val (offsetX, offsetZ, y, group, owner, health, reinforcementKind, date) = tuple
                    val gid = ju.UUID.fromString(group)
                    val uid = ju.UUID.fromString(owner)
                    cs.blocks(BlockKey(offsetX, offsetZ, y)) = BlockState(gid, uid, false, false, health, ReinforcementTypes.from(reinforcementKind).get, date.toInstant())
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
                val id = sql"""
                DELETE FROM BlockReinforcements
                    WHERE ChunkX = ${cx}
                      AND ChunkZ = ${cz}
                      AND World = ${cw}
                      AND OffsetX = ${key.offsetX}
                      AND OffsetZ = ${key.offsetZ}
                      AND Y = ${key.y}
                RETURNING ReinforcementID;
                """.map(rs => ju.UUID.fromString(rs.string("ReinforcementID"))).single.apply()
                sql"""
                DELETE FROM Reinforcements WHERE ID = ${id};
                """.update.apply()
            }
            value.blocks.view.filter(item => item._2.dirty && !item._2.deleted).foreach { item =>
                val (key, value) = item

                val id = sql"""
                SELECT ReinforcementID FROM BlockReinforcements
                    WHERE ChunkX = ${cx}
                      AND ChunkZ = ${cz}
                      AND World = ${cw}
                      AND OffsetX = ${key.offsetX}
                      AND OffsetZ = ${key.offsetZ}
                      AND Y = ${key.y};
                """.map(rs => ju.UUID.fromString(rs.string("ReinforcementID"))).single.apply()

                id match
                    case None =>
                        val id = sql"""
                        INSERT INTO Reinforcements
                            (GroupID, Owner, Health, ReinforcementKind, PlacedAt)
                            VALUES
                            (${value.group}, ${value.owner}, ${value.health}, ${value.kind.into()}, ${value.placedAt})
                        RETURNING ID;
                        """.map(rs => ju.UUID.fromString(rs.string("ID"))).single.apply().get

                        sql"""
                        INSERT OR REPLACE INTO BlockReinforcements
                            (ReinforcementID, ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y)
                            VALUES
                            (${id}, ${cx},  ${cz},  ${cw}, ${key.offsetX}, ${key.offsetZ}, ${key.y})
                        """.update.apply()
                    case Some(id) =>
                        sql"""
                        REPLACE INTO Reinforcements
                            (ID, GroupID, Owner, Health, ReinforcementKind, PlacedAt)
                            VALUES
                            (${id}, ${value.group}, ${value.owner}, ${value.health}, ${value.kind.into()}, ${value.placedAt});
                        """.update.apply()
            }
        }

        value.blocks.filterInPlace( (key, value) => !value.deleted )
        value.blocks.mapValuesInPlace ( (key, value) =>
            if value.dirty then
                value.copy(dirty = false)
            else
                value
        )