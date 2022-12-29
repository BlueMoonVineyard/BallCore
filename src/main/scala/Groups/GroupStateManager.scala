// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.Storage
import BallCore.DataStructures.LRUCache
import java.{util => ju}
import BallCore.Storage.Migration
import scalikejdbc._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

inline def uuid(from: String) = ju.UUID.fromString(from)

case class GroupStates(id: ju.UUID, name: String)
object GroupStates:
    def apply(ws: WrappedResultSet): GroupStates =
        GroupStates(uuid(ws.string("ID")), ws.string("Name"))

case class GroupMemberships(groupID: ju.UUID, userID: ju.UUID)

object GroupMemberships:
    def apply(ws: WrappedResultSet): GroupMemberships =
        GroupMemberships(uuid(ws.string("GroupID")), uuid(ws.string("UserID")))

case class GroupRoles(groupID: ju.UUID, roleID: ju.UUID, name: String, hoist: Boolean, permissions: Map[Permissions, RuleMode], ord: String)

object GroupRoles:
    def apply(ws: WrappedResultSet): GroupRoles =
        GroupRoles(
            groupID = uuid(ws.string("GroupID")),
            roleID = uuid(ws.string("RoleID")),
            name = ws.string("Name"),
            hoist = ws.boolean("Hoist"),
            permissions = decode[Map[Permissions, RuleMode]](ws.string("Permissions")).toOption.get,
            ord = ws.string("Ord")
        )

case class GroupRoleMemberships(groupID: ju.UUID, roleID: ju.UUID, userID: ju.UUID)

object GroupRoleMemberships:
    def apply(ws: WrappedResultSet): GroupRoleMemberships =
        GroupRoleMemberships(
            groupID = uuid(ws.string("GroupID")),
            roleID = uuid(ws.string("RoleID")),
            userID = uuid(ws.string("UserID")),
        )

case class GroupOwnerships(groupID: ju.UUID, userID: ju.UUID)

object GroupOwnerships:
    def apply(ws: WrappedResultSet): GroupOwnerships =
        GroupOwnerships(
            groupID = uuid(ws.string("GroupID")),
            userID = uuid(ws.string("UserID")),
        )

/** The GroupStateManager is responsible for loading and saving GroupStates to disk as appropriate */
class GroupStateManager()(using sql: Storage.SQLManager):
    sql.applyMigration(
        Migration(
            "Initial Group State Manager",
            List(
                sql"""
                CREATE TABLE GroupStates (
                    ID text PRIMARY KEY,
                    Name text NOT NULL
                );
                """,
                sql"""
                CREATE TABLE GroupMemberships (
                    GroupID text NOT NULL,
                    UserID text NOT NULL,
                    UNIQUE(GroupID, UserID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID)
                );
                """,
                sql"""
                CREATE TABLE GroupRoles (
                    GroupID text NOT NULL,
                    RoleID text NOT NULL,
                    Name text NOT NULL,
                    Hoist boolean NOT NULL,
                    Permissions text NOT NULL,
                    Ord text NOT NULL,
                    UNIQUE(GroupID, RoleID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID)
                );
                """,
                sql"""
                CREATE TABLE GroupRoleMemberships (
                    GroupID text NOT NULL,
                    UserID text NOT NULL,
                    RoleID text NOT NULL,
                    UNIQUE(GroupID, RoleID, UserID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID),
                    FOREIGN KEY (GroupID, UserID) REFERENCES GroupMembership(GroupID, UserID)
                );
                """,
                sql"""
                CREATE TABLE GroupOwnerships (
                    GroupID text NOT NULL,
                    UserID text NOT NULL,
                    UNIQUE(GroupID, RoleID, UserID),
                    FOREIGN KEY (GroupID, UserID) REFERENCES GroupMembership(GroupID, UserID)
                );
                """
            ),
            List(
                sql"DROP TABLE GroupOwnerships;",
                sql"DROP TABLE GroupRoleMemberships;",
                sql"DROP TABLE GroupRoles;",
                sql"DROP TABLE GroupMemberships;",
                sql"DROP TABLE GroupStates;",
            )
        )
    )
    implicit val session: DBSession = AutoSession
    val cache = LRUCache[GroupID, GroupState](1000, evict)

    private def evict(group: GroupID, state: GroupState): Unit =
        kvs.set("GroupStates", group.toString(), state)

    private def save(group: GroupID): Unit =
        if !cache.contains(group) then
            return
        kvs.set("GroupStates", group.toString(), cache(group))

    private def load(group: GroupID): Unit =
        val gs_ = sql"SELECT * FROM GroupStates WHERE ID = ${group}".map(GroupStates.apply).single.apply()
        gs_ match
            case None => return
            case Some(_) => ()
        val gs = gs_.get

        val gm = sql"SELECT * FROM GroupMemberships WHERE GroupID = ${group}".map(GroupMemberships.apply).list.apply()
        val gr = sql"SELECT * FROM GroupRoles WHERE GroupID = ${group}".map(GroupRoles.apply).list.apply()
        val grm = sql"SELECT * FROM GroupRoleMemberships WHERE GroupID = ${group}".map(GroupRoleMemberships.apply).list.apply()
        val gro = sql"SELECT * FROM GroupOwnerships WHERE GroupID = ${group}".map(GroupOwnerships.apply).list.apply()

        val state = GroupState(
            name = gs.name,
            owners = gro.map(_.userID),
            roles = gr.map { role =>
                RoleState(
                    id = role.roleID,
                    name = role.name,
                    hoist = role.hoist,
                    permissions = role.permissions,
                )
            },
            users = gm.map { membership =>
                (membership.userID, grm.filter(_.userID == membership.userID).map(_.roleID).toSet)
            }.toMap
        )
        cache(group) = state

    def remove(group: GroupID): Unit =
        cache.remove(group)
        kvs.remove("GroupStates", group.toString())

    def set(group: GroupID, state: GroupState): Unit =
        cache(group) = state
        save(group)

    def get(group: GroupID): Option[GroupState] =
        if !cache.contains(group) then
            load(group)
        cache.get(group)

    def check(perm: Permissions, user: UserID, group: GroupID): Boolean =
        if !cache.contains(group) then
            load(group)
        cache(group).check(perm, user)