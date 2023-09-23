// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Storage

import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor
import BallCore.DataStructures.LRUCache
import java.util.UUID

class EntityStateManager()(using sql: Storage.SQLManager, csm: ChunkStateManager):
    val _ = csm
    sql.applyMigration(
        Storage.Migration(
            "Initial EntityStateManager",
            List(
                sql"""
                CREATE TABLE EntityReinforcements (
                    EntityID TEXT NOT NULL,
                    ReinforcementID TEXT NOT NULL,
                    UNIQUE(EntityID),
                    FOREIGN KEY (ReinforcementID) REFERENCES Reinforcements(ID) ON DELETE CASCADE
                );
                """,
            ),
            List(
                sql"""
                DROP TABLE EntityReinforcements;
                """,
            ),
        )
    )

    private implicit val session: DBSession = sql.session
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
        sql.localTx { implicit session =>
            if value.deleted then
                val id = sql"""
                DELETE FROM EntityReinforcements
                    WHERE EntityID = ${key}
                RETURNING ReinforcementID;
                """.map(rs => UUID.fromString(rs.string("ReinforcementID"))).single.apply()
                sql"""
                DELETE FROM Reinforcements WHERE ID = ${id};
                """.update.apply(); ()
            else if value.dirty then
                val id = sql"""
                SELECT ReinforcementID FROM BlockReinforcements
                    WHERE EntityID = ${key};
                """.map(rs => UUID.fromString(rs.string("ReinforcementID"))).single.apply()

                id match
                    case None =>
                        val id = sql"""
                        INSERT INTO Reinforcements
                            (GroupID, Owner, Health, ReinforcementKind, PlacedAt)
                            VALUES
                            (${value.group}, ${value.owner}, ${value.health}, ${value.kind.into()}, ${value.placedAt})
                        RETURNING ID;
                        """.map(rs => UUID.fromString(rs.string("ID"))).single.apply().get

                        sql"""
                        INSERT INTO EntityReinforcements
                            (ReinforcementID, EntityID)
                            VALUES
                            (${id}, ${key});
                        """.update.apply(); ()
                    case Some(id) =>
                        sql"""
                        REPLACE INTO Reinforcements
                            (ID, GroupID, Owner, Health, ReinforcementKind, PlacedAt)
                            VALUES
                            (${id}, ${value.group}, ${value.owner}, ${value.health}, ${value.kind.into()}, ${value.placedAt});
                        """.update.apply(); ()
        }

    private def load(key: UUID): Unit =
        val item =
            sql"""
            SELECT GroupID, SubgroupID, Owner, Health, ReinforcementKind, PlacedAt FROM EntityReinforcements
                LEFT JOIN Reinforcements
                       ON Reinforcements.ID = EntityReinforcements.ReinforcementID

                WHERE EntityID = ${key};
            """
                .map[ReinforcementState]{ rs =>
                    val tuple = (rs.string("GroupID"), rs.string("SubgroupID"), rs.string("Owner"), rs.int("Health"), rs.string("ReinforcementKind"), rs.date("PlacedAt"))
                    val (group, subgroup, owner, health, reinforcementKind, date) = tuple
                    val gid = UUID.fromString(group)
                    val sgid = UUID.fromString(subgroup)
                    val uid = UUID.fromString(owner)
                    ReinforcementState(gid, sgid, uid, false, false, health, ReinforcementTypes.from(reinforcementKind).get, date.toInstant())
                }
                .single
                .apply()
        item.foreach(cache(key) = _)
