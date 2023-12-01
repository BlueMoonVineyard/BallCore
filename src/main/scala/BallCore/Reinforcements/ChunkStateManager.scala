// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.DataStructures.LRUCache
import BallCore.Storage
import cats.effect.IO
import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.Map

val reinforcementKindCodec = text.imap { str =>
    ReinforcementTypes.from(str).get
} { it => it.into() }

/** The ChunkStateManager is responsible for managing the loading and saving of
  * chunkstates to a SQL database
  */
class ChunkStateManager()(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial ChunkStateManager",
            List(
                sql"""
                CREATE TABLE Reinforcements (
                    ID UUID PRIMARY KEY,
                    GroupID UUID NOT NULL,
                    SubgroupID UUID,
                    Owner UUID NOT NULL,
                    Health INTEGER NOT NULL,
                    ReinforcementKind TEXT NOT NULL,
                    PlacedAt TIMESTAMPTZ NOT NULL,
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID),
                    FOREIGN KEY (SubgroupID) REFERENCES Subgroups(SubgroupID)
                );
                """.command,
                sql"""
                CREATE TABLE BlockReinforcements (
                    ChunkX INTEGER NOT NULL,
                    ChunkZ INTEGER NOT NULL,
                    World UUID NOT NULL,
                    OffsetX INTEGER NOT NULL,
                    OffsetZ INTEGER NOT NULL,
                    Y INTEGER NOT NULL,
                    ReinforcementID UUID NOT NULL,
                    UNIQUE(ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y),
                    FOREIGN KEY (ReinforcementID) REFERENCES Reinforcements(ID) ON DELETE CASCADE
                );
                """.command,
            ),
            List(
                sql"""
                DROP TABLE BlockReinforcements;
                """.command,
                sql"""
                DROP TABLE Reinforcements;
                """.command,
            ),
        )
    )

    private val cache = LRUCache[ChunkKey, ChunkState](1000, evict)

    def evictAll(): Unit =
        sql.useBlocking(cache.toList.traverse { (x, y) =>
            sql.withS(set(x, y))
        })
        cache.clear()

    private def evict(key: ChunkKey, value: ChunkState): Unit =
        sql.useBlocking(sql.withS(set(key, value)))

    private def load(key: ChunkKey)(using Session[IO]): IO[Unit] =
        sql
            .queryListIO(
                sql"""
        SELECT OffsetX, OffsetZ, Y, GroupID, SubgroupID, Owner, Health, ReinforcementKind, PlacedAt FROM BlockReinforcements
            LEFT JOIN Reinforcements
                ON Reinforcements.ID = BlockReinforcements.ReinforcementID

            WHERE ChunkX = $int4
              AND ChunkZ = $int4
              AND World = $uuid
        """,
                int4 *: int4 *: int4 *: uuid *: uuid *: uuid *: int4 *: reinforcementKindCodec *: timestamptz,
                (key.chunkX, key.chunkZ, key.world),
            )
            .flatMap { items =>
                IO {
                    val cs = ChunkState(mutable.Map())
                    items.foreach { tuple =>
                        val (
                            offsetX,
                            offsetZ,
                            y,
                            group,
                            subgroup,
                            owner,
                            health,
                            reinforcementKind,
                            date,
                        ) = tuple
                        cs.blocks(BlockKey(offsetX, offsetZ, y)) =
                            ReinforcementState(
                                group,
                                subgroup,
                                owner,
                                false,
                                false,
                                health,
                                reinforcementKind,
                                date,
                            )
                    }
                    cache(key) = cs
                }
            }

    def get(key: ChunkKey): ChunkState =
        if !cache.contains(key) then sql.useBlocking(sql.withS(load(key)))
        cache(key)

    def set(key: ChunkKey, value: ChunkState)(using Session[IO]): IO[Unit] =
        val cx = key.chunkX
        val cz = key.chunkZ
        val cw = key.world
        val _ = (cx, cz, cw)

        sql.txIO { tx =>
            value.blocks.toList
                .traverse_ { tuple =>
                    val (key, item) = tuple
                    if item.deleted then
                        sql
                            .queryOptionIO(
                                sql"""
                    DELETE FROM BlockReinforcements
                        WHERE ChunkX = $int4
                          AND ChunkZ = $int4
                          AND World = $uuid
                          AND OffsetX = $int4
                          AND OffsetZ = $int4
                          AND Y = $int4
                    RETURNING ReinforcementID;
                    """,
                                uuid,
                                (cx, cz, cw, key.offsetX, key.offsetZ, key.y),
                            )
                            .flatMap {
                                case None => IO.unit
                                case Some(it) =>
                                    sql
                                        .commandIO(
                                            sql"""
                                DELETE FROM Reinforcements WHERE ID = $uuid
                                """,
                                            it,
                                        )
                                        .map(_ => ())
                            }
                    else if item.dirty && !item.deleted then
                        for {
                            id <- sql.queryOptionIO(
                                sql"""
                        SELECT ReinforcementID FROM BlockReinforcements
                            WHERE ChunkX = $int4
                              AND ChunkZ = $int4
                              AND World = $uuid
                              AND OffsetX = $int4
                              AND OffsetZ = $int4
                              AND Y = $int4;
                        """,
                                uuid,
                                (cx, cz, cw, key.offsetX, key.offsetZ, key.y),
                            )

                            _ <- id match
                                case Some(id) =>
                                    sql.commandIO(
                                        sql"""
                                UPDATE Reinforcements SET GroupID = $uuid, Owner = $uuid, Health = $int4, ReinforcementKind = $reinforcementKindCodec, PlacedAt = $timestamptz WHERE ID = $uuid;
                                """,
                                        (
                                            item.group,
                                            item.owner,
                                            item.health,
                                            item.kind,
                                            item.placedAt,
                                            id,
                                        ),
                                    )
                                case None =>
                                    val newID = UUID.randomUUID()
                                    for {
                                        _ <- sql.commandIO(
                                            sql"""
                                    INSERT INTO Reinforcements
                                        (ID, GroupID, Owner, Health, ReinforcementKind, PlacedAt)
                                    VALUES
                                        ($uuid, $uuid, $uuid, $int4, $reinforcementKindCodec, $timestamptz)
                                    """,
                                            (
                                                newID,
                                                item.group,
                                                item.owner,
                                                item.health,
                                                item.kind,
                                                item.placedAt,
                                            ),
                                        )
                                        _ <- sql.commandIO(
                                            sql"""
                                    INSERT INTO BlockReinforcements
                                        (ReinforcementID, ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y)
                                    VALUES
                                        ($uuid, $int4, $int4, $uuid, $int4, $int4, $int4)
                                    """,
                                            (
                                                newID,
                                                cx,
                                                cz,
                                                cw,
                                                key.offsetX,
                                                key.offsetZ,
                                                key.y,
                                            ),
                                        )
                                    } yield ()
                        } yield ()
                    else IO.unit
                }
                .flatMap { _ =>
                    IO {
                        value.blocks
                            .filterInPlace((key, value) => !value.deleted)
                        value.blocks.mapValuesInPlace((key, value) =>
                            if value.dirty then value.copy(dirty = false)
                            else value
                        )
                        ()
                    }
                }
        }
