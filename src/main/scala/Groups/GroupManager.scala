// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.Storage
import BallCore.DataStructures.Lexorank
import BallCore.Groups.Extensions._
import scalikejdbc._
import java.{util => ju}
import io.circe._, io.circe.generic.semiauto._, io.circe.parser._, io.circe.syntax._

inline def uuid(from: String) = ju.UUID.fromString(from)

case class GroupStates(id: ju.UUID, name: String):
    def save()(implicit session: DBSession): Unit =
        sql"REPLACE INTO GroupStates (ID, Name) VALUES (${id}, ${name});".update.apply()
object GroupStates:
    def apply(ws: WrappedResultSet): GroupStates =
        GroupStates(uuid(ws.string("ID")), ws.string("Name"))

given Decoder[GroupStates] = deriveDecoder[GroupStates]
given Encoder[GroupStates] = deriveEncoder[GroupStates]

case class GroupMemberships(groupID: ju.UUID, userID: ju.UUID):
    def save()(implicit session: DBSession): Unit =
        sql"REPLACE INTO GroupMemberships (GroupID, UserID) VALUES (${groupID}, ${userID});".update.apply()
object GroupMemberships:
    def apply(ws: WrappedResultSet): GroupMemberships =
        GroupMemberships(uuid(ws.string("GroupID")), uuid(ws.string("UserID")))

case class GroupRoles(groupID: ju.UUID, roleID: ju.UUID, name: String, hoist: Boolean, permissions: Map[Permissions, RuleMode], ord: String):
    def save()(implicit session: DBSession): Unit =
        sql"""
        REPLACE INTO GroupRoles
            (GroupID, RoleID, Name, Hoist, Permissions, Ord)
        VALUES
            (${groupID}, ${roleID}, ${name}, ${hoist}, ${permissions.asJson.noSpaces}, ${ord});
        """.update.apply()
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

case class GroupRoleMemberships(groupID: ju.UUID, roleID: ju.UUID, userID: ju.UUID):
    def save()(implicit session: DBSession): Unit =
        sql"""
        REPLACE INTO GroupRoleMemberships
            (GroupID, RoleID, UserID)
        VALUES
            ($groupID, $roleID, $userID);
        """.update.apply()
object GroupRoleMemberships:
    def apply(ws: WrappedResultSet): GroupRoleMemberships =
        GroupRoleMemberships(
            groupID = uuid(ws.string("GroupID")),
            roleID = uuid(ws.string("RoleID")),
            userID = uuid(ws.string("UserID")),
        )

case class GroupOwnerships(groupID: ju.UUID, userID: ju.UUID):
    def save()(implicit session: DBSession): Unit =
        sql"""
        REPLACE INTO GroupOwnerships
            (GroupID, UserID)
        VALUES
            ($groupID, $userID);
        """.update.apply()
object GroupOwnerships:
    def apply(ws: WrappedResultSet): GroupOwnerships =
        GroupOwnerships(
            groupID = uuid(ws.string("GroupID")),
            userID = uuid(ws.string("UserID")),
        )

enum GroupError:
    case MustBeOwner
    case MustBeOnlyOwner
    case GroupNotFound
    case TargetNotInGroup
    case GroupWouldHaveNoOwners
    case TargetIsAlreadyOwner
    case AlreadyInGroup
    case NoPermissions
    case RoleNotFound
    case RoleAboveYours
    case CantAssignEveryone

/** The GroupManager implements all of the logic relating to group management and permission */
class GroupManager()(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
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
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE
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
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE
                );
                """,
                sql"""
                CREATE TABLE GroupRoleMemberships (
                    GroupID text NOT NULL,
                    UserID text NOT NULL,
                    RoleID text NOT NULL,
                    UNIQUE(GroupID, RoleID, UserID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE,
                    FOREIGN KEY (GroupID, UserID) REFERENCES GroupMemberships(GroupID, UserID) ON DELETE CASCADE
                );
                """,
                sql"""
                CREATE TABLE GroupOwnerships (
                    GroupID text NOT NULL,
                    UserID text NOT NULL,
                    UNIQUE(GroupID, UserID),
                    FOREIGN KEY (GroupID, UserID) REFERENCES GroupMemberships(GroupID, UserID) ON DELETE CASCADE
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

    // private def get(group: GroupID): Either[GroupError, GroupState] =
    //     gsm.get(group) match
    //         case Some(value) => Right(value)
    //         case None => Left(GroupError.GroupNotFound)
    def createGroup(owner: UserID, name: String): GroupID =
        val newGroupID = ju.UUID.randomUUID()
        val roles: List[RoleState] = List(
            RoleState(
                ju.UUID.randomUUID(),
                "Admin", true,
                Map(
                    (Permissions.ManageRoles, RuleMode.Allow),
                    (Permissions.ManageUserRoles, RuleMode.Allow),
                    (Permissions.InviteUser, RuleMode.Allow),
                    (Permissions.RemoveUser, RuleMode.Allow),
                    (Permissions.UpdateGroupInformation, RuleMode.Allow),
                    (Permissions.AddReinforcements, RuleMode.Allow),
                    (Permissions.RemoveReinforcements, RuleMode.Allow),
                ),
            ),
            RoleState(
                ju.UUID.randomUUID(),
                "Moderator", true,
                Map(
                    (Permissions.ManageUserRoles, RuleMode.Allow),
                    (Permissions.InviteUser, RuleMode.Allow),
                    (Permissions.RemoveUser, RuleMode.Allow),
                ),
            ),
            RoleState(
                nullUUID,
                "Everyone", true,
                Map(
                ),
            ),
        )
        DB.localTx { implicit session =>
            GroupStates(newGroupID, name).save()
            GroupMemberships(newGroupID, owner).save()
            GroupOwnerships(newGroupID, owner).save()
            var ord = ""
            roles.foreach { role =>
                GroupRoles(newGroupID, role.id, role.name, role.hoist, role.permissions, Lexorank.rank(ord, "")).save()
                ord = Lexorank.rank(ord, "")
            }
            GroupRoleMemberships(newGroupID, nullUUID, owner).save()
        }
        newGroupID

    def deleteGroup(as: UserID, group: GroupID): Either[GroupError, Unit] =
        val owners = getGroupOwners(group)
        Right(())
            .guard(GroupError.MustBeOwner) { _ => owners.view.map(_.userID).contains(as) }
            .guard(GroupError.MustBeOnlyOwner) { _ => owners.length == 1 }
            .map { _ =>
                sql"DELETE FROM GroupStates WHERE ID = ${group};".update.apply()
            }

    private def getGroupOwners(group: GroupID): List[GroupOwnerships] =
        sql"SELECT * FROM GroupOwnerships WHERE GroupID = ${group}".map(GroupOwnerships.apply).list.apply()

    private def getGroupMembers(group: GroupID): List[GroupMemberships] =
        sql"SELECT * FROM GroupMemberships WHERE GroupID = ${group}".map(GroupMemberships.apply).list.apply()

    private def getAll(group: GroupID): Either[GroupError, GroupState] =
        val gs_ = sql"SELECT * FROM GroupStates WHERE ID = ${group}".map(GroupStates.apply).single.apply()
        gs_ match
            case None => return Left(GroupError.GroupNotFound)
            case Some(_) => ()
        val gs = gs_.get

        val gm = sql"SELECT * FROM GroupMemberships WHERE GroupID = ${group}".map(GroupMemberships.apply).list.apply()
        val gr = sql"SELECT * FROM GroupRoles WHERE GroupID = ${group} ORDER BY Ord".map(GroupRoles.apply).list.apply()
        val grm = sql"SELECT * FROM GroupRoleMemberships WHERE GroupID = ${group}".map(GroupRoleMemberships.apply).list.apply()
        val gro = sql"SELECT * FROM GroupOwnerships WHERE GroupID = ${group}".map(GroupOwnerships.apply).list.apply()

        Right(GroupState(
            metadata = gs,
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
        ))

    def promoteToOwner(as: UserID, target: UserID, group: GroupID): Either[GroupError, Unit] =
        val owners = getGroupOwners(group)
        val members = getGroupMembers(group)
        Right(())
            .guard(GroupError.MustBeOwner) { _ => owners.view.map(_.userID).contains(as) }
            .guard(GroupError.TargetNotInGroup) { _ => members.view.map(_.userID).contains(target) }
            .guard(GroupError.TargetIsAlreadyOwner) { _ => !owners.view.map(_.userID).contains(target) }
            .map { _ =>
                sql"INSERT INTO GroupOwnerships (GroupID, UserID) VALUES ($group, $target);".update.apply()
            }

    def giveUpOwnership(as: UserID, group: GroupID): Either[GroupError, Unit] =
        val owners = getGroupOwners(group)

        Right(owners.map(_.userID))
            .guard(GroupError.MustBeOwner) { _.contains(as) }
            .guard(GroupError.GroupWouldHaveNoOwners) { _.length > 1 }
            .map { _ =>
                sql"DELETE FROM GroupOwnerships WHERE GroupID = $group AND UserID = $as;".update.apply()
            }

    def roles(group: GroupID): Either[GroupError, List[RoleState]] =
        val gr = sql"SELECT * FROM GroupRoles WHERE GroupID = ${group}".map(GroupRoles.apply).list.apply()
        Right(gr.map { role =>
            RoleState(
                id = role.roleID,
                name = role.name,
                hoist = role.hoist,
                permissions = role.permissions,
            )
        })

    def assignRole(as: UserID, target: UserID, group: GroupID, role: RoleID, has: Boolean): Either[GroupError, Unit] =
        getAll(group)
            .guard(GroupError.NoPermissions) { _.check(Permissions.ManageUserRoles, as) }
            .guard(GroupError.RoleNotFound) { _.roles.exists(_.id == role) }
            .guard(GroupError.TargetNotInGroup) { _.users.contains(target) }
            .guard(GroupError.CantAssignEveryone) { _ => nullUUID != role }
            .flatMap { data =>
                if data.owners.contains(as) then
                    Right(data)
                else
                    val myRoles = data.users(as)
                    val highestRoleIdx = data.roles.indexOf(data.roles.filter(r => myRoles.contains(r.id))(0))
                    val targetRoleIdx = data.roles.indexWhere(_.id == role)
                    if targetRoleIdx <= highestRoleIdx then
                        Left(GroupError.RoleAboveYours)
                    else
                        Right(data)
            }.map { data =>
                if has then
                    sql"REPLACE INTO GroupRoleMemberships (GroupID, RoleID, UserID) VALUES ($group, $role, $target);".update.apply()
                else
                    sql"DELETE FROM GroupRoleMemberships WHERE GroupID = $group AND RoleID = $role AND UserID = $target".update.apply()
            }

    // TODO: invites/passwords
    def addToGroup(user: UserID, group: GroupID): Either[GroupError, Unit] =
        val members = getGroupMembers(group)
        Right(())
            .guard(GroupError.AlreadyInGroup) { _ => !members.view.map(_.userID).contains(user) }
            .map { data =>
                sql"INSERT INTO GroupMemberships (GroupID, UserID) VALUES ($group, $user);".update.apply()
            }

    def userGroups(userID: UserID): Either[GroupError, List[GroupStates]] =
        Right(sql"""
        SELECT
            GroupStates.*
        FROM
            GroupStates, GroupMemberships
        WHERE
            GroupMemberships.UserID = $userID
            AND GroupStates.ID = GroupMemberships.GroupID;
        """.map(GroupStates.apply).list.apply())

    /// relatively heavy function, call once and cache only if you need to frequently consult permissions
    def getGroup(groupID: GroupID): Either[GroupError, GroupState] =
        getAll(groupID)

    def check(user: UserID, group: GroupID, permission: Permissions): Either[GroupError, Boolean] =
        getAll(group).map(_.check(permission, user))

    def checkE(user: UserID, group: GroupID, permission: Permissions): Either[GroupError, Unit] =
        getAll(group).guard(GroupError.NoPermissions) { _.check(permission, user) }.map { _ => () }