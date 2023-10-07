// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Storage

import BallCore.DataStructures.LRUCache
import java.util.UUID
import skunk.implicits._
import skunk.codec.all._
import cats.effect.IO
import skunk.data.Completion
import skunk.Session

class EntityStateManager()(using sql: Storage.SQLManager, csm: ChunkStateManager):
    val _ = csm
    sql.applyMigration(
        Storage.Migration(
            "Initial EntityStateManager",
            List(
                sql"""
                CREATE TABLE EntityReinforcements (
                    EntityID UUID NOT NULL,
                    ReinforcementID UUID NOT NULL,
                    UNIQUE(EntityID),
                    FOREIGN KEY (ReinforcementID) REFERENCES Reinforcements(ID) ON DELETE CASCADE
                );
                """.command,
            ),
            List(
                sql"""
                DROP TABLE EntityReinforcements;
                """.command,
            ),
        )
    )

    private val cache = LRUCache[UUID, ReinforcementState](1000, evict)
    private val evict = save

    def evictAll(): Unit =
        cache.foreach { (x, y) => save(x, y) }
        cache.clear()
    def get(key: UUID): Option[ReinforcementState] =
        if !cache.contains(key) then
            load(key)

        cache.get(key) match
            case Some(x) if !x.deleted => Some(x)
            case _ => None
    def set(key: UUID, value: ReinforcementState): Unit =
        cache(key) = value
    private def save(key: UUID, value: ReinforcementState): Unit =
        sql.useBlocking {
            sql.txIO { tx =>
                if value.deleted then
                    sql.queryOptionIO(sql"""
                    DELETE FROM EntityReinforcements
                        WHERE EntityID = $uuid
                    RETURNING ReinforcementID;
                    """, uuid, key).flatMap { id =>
                        id.map { it =>
                            sql.commandIO(sql"""
                            DELETE FROM Reinforcements WHERE ID = $uuid;
                            """, it).map(_ => ())
                        }.getOrElse(IO.unit)
                    }
                else if value.dirty then
                    for {
                        id <- sql.queryOptionIO(sql"""
                              SELECT ReinforcementID FROM EntityReinforcements
                                  WHERE EntityID = $uuid;
                              """, uuid, key)
                        _ <- id match
                            case Some(id) =>
                                sql.commandIO(sql"""
                                UPDATE Reinforcements SET GroupID = $uuid, Owner = $uuid, Health = $int4, ReinforcementKind = $text, PlacedAt = $timestamptz WHERE ID = $uuid;
                                """, (value.group, value.owner, value.health, value.kind.into(), value.placedAt, id))
                            case None =>
                                val newID = UUID.randomUUID()
                                for {
                                    _ <- sql.commandIO(sql"""
                                    INSERT INTO Reinforcements
                                        (ID, GroupID, Owner, Health, ReinforcementKind, PlacedAt)
                                    VALUES
                                        ($uuid, $uuid, $uuid, $int4, $text, $timestamptz)
                                    """, (newID, value.group, value.owner, value.health, value.kind.into(), value.placedAt))
                                    _ <- sql.commandIO(sql"""
                                    INSERT INTO EntityReinforcements
                                        (ReinforcementID, EntityID)
                                    VALUES
                                        ($uuid, $uuid)
                                    """, (newID, key))
                                } yield ()
                    } yield ()
                else
                    IO.unit
            }
        }

    private def load(key: UUID): Unit =
        val item =
            sql.useBlocking(sql.queryOptionIO(sql"""
            SELECT GroupID, SubgroupID, Owner, Health, ReinforcementKind, PlacedAt FROM EntityReinforcements
                LEFT JOIN Reinforcements
                       ON Reinforcements.ID = EntityReinforcements.ReinforcementID

                WHERE EntityID = $uuid;
            """, (uuid *: uuid *: uuid *: int4 *: text *: timestamptz), key).map { opt =>
                    opt.map { it =>
                        val (gid, sgid, owner, health, kind, date) = it
                        ReinforcementState(gid, sgid, owner, false, false, health, ReinforcementTypes.from(kind).get, date)
                    }
                })

        item.foreach(cache(key) = _)
