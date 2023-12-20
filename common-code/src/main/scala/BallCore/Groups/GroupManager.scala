// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.DataStructures.Lexorank
import BallCore.Groups.Extensions.*
import BallCore.Storage
import BallCore.Storage.SQLManager
import cats.data.EitherT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all.*
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import skunk.Session
import skunk.circe.codec.all.*
import skunk.codec.all.*
import skunk.data.Completion
import skunk.implicits.*

import java.util as ju
import scala.jdk.CollectionConverters.*
import skunk.Transaction

case class GroupStates(id: ju.UUID, name: String):
    def save()(using sql: SQLManager)(using Session[IO]): IO[Unit] =
        sql
            .commandIO(
                sql"""
        INSERT INTO GroupStates (ID, Name) VALUES ($uuid, $text) ON CONFLICT (ID) DO UPDATE SET Name = EXCLUDED.Name;
        """,
                (id, name),
            )
            .map(_ => ())

given Decoder[GroupStates] = deriveDecoder[GroupStates]
given Encoder[GroupStates] = deriveEncoder[GroupStates]

case class GroupMemberships(groupID: ju.UUID, userID: ju.UUID):
    def save()(using sql: SQLManager)(using Session[IO]): IO[Unit] =
        sql
            .commandIO(
                sql"""
        INSERT INTO GroupMemberships (GroupID, UserID) VALUES ($uuid, $uuid);
        """,
                (groupID, userID),
            )
            .map(_ => ())

case class GroupRoles(
    groupID: ju.UUID,
    roleID: ju.UUID,
    name: String,
    hoist: Boolean,
    permissions: Map[Permissions, RuleMode],
    ord: String,
):
    def save()(using sql: SQLManager)(using Session[IO]): IO[Unit] =
        sql
            .commandIO(
                sql"""
        INSERT INTO GroupRoles
            (GroupID, RoleID, Name, Hoist, Permissions, Ord)
        VALUES
            ($uuid, $uuid, $text, $bool, $jsonb, $text)
        ON CONFLICT (GroupID, RoleID) DO UPDATE SET
            Name = EXCLUDED.Name,
            Hoist = EXCLUDED.Hoist,
            Permissions = EXCLUDED.Permissions,
            Ord = EXCLUDED.Ord;
        """,
                (groupID, roleID, name, hoist, permissions.asJson, ord),
            )
            .map(_ => ())

case class GroupRoleMemberships(
    groupID: ju.UUID,
    roleID: ju.UUID,
    userID: ju.UUID,
):
    def save()(using sql: SQLManager)(using Session[IO]): IO[Unit] =
        sql
            .commandIO(
                sql"""
        INSERT INTO GroupRoleMemberships
            (GroupID, RoleID, UserID)
        VALUES
            ($uuid, $uuid, $uuid);
        """,
                (groupID, roleID, userID),
            )
            .map(_ => ())

case class GroupOwnerships(groupID: ju.UUID, userID: ju.UUID):
    def save()(using sql: SQLManager)(using Session[IO]): IO[Unit] =
        sql
            .commandIO(
                sql"""
        INSERT INTO GroupOwnerships
            (GroupID, UserID)
        VALUES
            ($uuid, $uuid);
        """,
                (groupID, userID),
            )
            .map(_ => ())

object GroupError:
    given Encoder[GroupError] = deriveEncoder[GroupError]

    given Decoder[GroupError] = deriveDecoder[GroupError]

enum GroupError:
    case MustBeOwner
    case MustNotBeOwner
    case MustBeOnlyOwner
    case MustBeInGroup
    case GroupNotFound
    case TargetIsAboveYou
    case TargetNotInGroup
    case GroupWouldHaveNoOwners
    case TargetIsAlreadyOwner
    case AlreadyInGroup
    case NoPermissions
    case RoleNotFound
    case RoleAboveYours
    case CantAssignEveryone
    case MustHavePermission

    def explain(): String =
        this match
            case MustBeOwner => "you must be owner"
            case MustNotBeOwner => "you must not be an owner"
            case MustBeOnlyOwner => "you must be the only owner"
            case MustBeInGroup => "you must be in the group"
            case GroupNotFound => "the group was not found"
            case TargetNotInGroup => "the target is not in the group"
            case GroupWouldHaveNoOwners => "the group would have no owners"
            case TargetIsAlreadyOwner => "the target is already an owner"
            case TargetIsAboveYou => "the target's roles outrank your own"
            case AlreadyInGroup => "the target is already in the group"
            case NoPermissions => "you do not have permission"
            case RoleNotFound => "the role was not found"
            case RoleAboveYours => "the role was above your topmost role"
            case CantAssignEveryone => "you cannot assign the everyone role"
            case MustHavePermission =>
                "you must have the permission in order to give it to others"

/** The GroupManager implements all of the logic relating to group management
  * and permission
  */
class GroupManager()(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial Group State Manager",
            List(
                sql"""
                CREATE TABLE GroupStates (
                    ID UUID PRIMARY KEY,
                    Name TEXT NOT NULL
                );
                """.command,
                sql"""
                CREATE TABLE GroupMemberships (
                    GroupID UUID NOT NULL,
                    UserID UUID NOT NULL,
                    UNIQUE(GroupID, UserID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE
                );
                """.command,
                sql"""
                CREATE TABLE GroupRoles (
                    GroupID UUID NOT NULL,
                    RoleID UUID NOT NULL,
                    Name TEXT NOT NULL,
                    Hoist BOOLEAN NOT NULL,
                    Permissions JSONB NOT NULL,
                    Ord TEXT NOT NULL,
                    UNIQUE(GroupID, RoleID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE
                );
                """.command,
                sql"""
                CREATE TABLE GroupRoleMemberships (
                    GroupID UUID NOT NULL,
                    UserID UUID NOT NULL,
                    RoleID UUID NOT NULL,
                    UNIQUE(GroupID, RoleID, UserID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE,
                    FOREIGN KEY (GroupID, UserID) REFERENCES GroupMemberships(GroupID, UserID) ON DELETE CASCADE
                );
                """.command,
                sql"""
                CREATE TABLE GroupOwnerships (
                    GroupID UUID NOT NULL,
                    UserID UUID NOT NULL,
                    UNIQUE(GroupID, UserID),
                    FOREIGN KEY (GroupID, UserID) REFERENCES GroupMemberships(GroupID, UserID) ON DELETE CASCADE
                );
                """.command,
            ),
            List(
                sql"DROP TABLE GroupOwnerships;".command,
                sql"DROP TABLE GroupRoleMemberships;".command,
                sql"DROP TABLE GroupRoles;".command,
                sql"DROP TABLE GroupMemberships;".command,
                sql"DROP TABLE GroupStates;".command,
            ),
        )
    )
    sql.applyMigration(
        Storage.Migration(
            "Add subgroups and subgroup permissions",
            List(
                sql"""
                CREATE TABLE Subgroups (
                    ParentGroup UUID NOT NULL,
                    SubgroupID UUID NOT NULL,
                    Name TEXT NOT NULL,
                    UNIQUE(SubgroupID),
                    FOREIGN KEY (ParentGroup) REFERENCES GroupStates(ID) ON DELETE CASCADE
                );
                """.command,
                sql"""
                CREATE TABLE SubgroupPermissions (
                    GroupID UUID NOT NULL,
                    SubgroupID UUID NOT NULL,
                    RoleID UUID NOT NULL,
                    Permissions JSONB NOT NULL,
                    UNIQUE(SubgroupID, RoleID),
                    FOREIGN KEY (SubgroupID) REFERENCES Subgroups(SubgroupID) ON DELETE CASCADE,
                    FOREIGN KEY (GroupID, RoleID) REFERENCES GroupRoles(GroupID, RoleID) ON DELETE CASCADE
                );
                """.command,
            ),
            List(
                sql"DROP TABLE SubgroupPermissions;".command,
                sql"DROP TABLE Subgroups;".command,
            ),
        )
    )
    sql.applyMigration(
        Storage.Migration(
            "Add subgroup claims",
            List(
                sql"""
                CREATE TABLE SubgroupClaims (
                    GroupID UUID NOT NULL,
                    SubgroupID UUID NOT NULL,
                    Claims JSONB NOT NULL,
                    UNIQUE(SubgroupID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE,
                    FOREIGN KEY (SubgroupID) REFERENCES Subgroups(SubgroupID) ON DELETE CASCADE
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE SubgroupClaims;
                """.command
            ),
        )
    )

    given runtime: IORuntime = sql.runtime

    given gm: GroupManager = this

    val invites: InviteManager = InviteManager()

    // private def get(group: GroupID): Either[GroupError, GroupState] =
    //     gsm.get(group) match
    //         case Some(value) => Right(value)
    //         case None => Left(GroupError.GroupNotFound)
    def createGroup(owner: UserID, name: String)(using
        Session[IO],
        Transaction[IO],
    ): IO[GroupID] =
        val newGroupID = ju.UUID.randomUUID()
        val roles: List[RoleState] = List(
            RoleState(
                ju.UUID.randomUUID(),
                "Admin",
                true,
                Map(
                    (Permissions.ManageRoles, RuleMode.Allow),
                    (Permissions.ManageUserRoles, RuleMode.Allow),
                    (Permissions.InviteUser, RuleMode.Allow),
                    (Permissions.RemoveUser, RuleMode.Allow),
                    (Permissions.UpdateGroupInformation, RuleMode.Allow),
                    (Permissions.ManageClaims, RuleMode.Allow),
                    (Permissions.ManageSubgroups, RuleMode.Allow),
                    (Permissions.AddReinforcements, RuleMode.Allow),
                    (Permissions.RemoveReinforcements, RuleMode.Allow),
                ),
                "U",
            ),
            RoleState(
                ju.UUID.randomUUID(),
                "Moderator",
                true,
                Map(
                    (Permissions.ManageUserRoles, RuleMode.Allow),
                    (Permissions.InviteUser, RuleMode.Allow),
                    (Permissions.RemoveUser, RuleMode.Allow),
                ),
                "g",
            ),
            RoleState(
                groupMemberUUID,
                "Group Members",
                true,
                Map(
                    (Permissions.Chests, RuleMode.Allow),
                    (Permissions.Doors, RuleMode.Allow),
                    (Permissions.Crops, RuleMode.Allow),
                    (Permissions.Build, RuleMode.Allow),
                    (Permissions.Entities, RuleMode.Allow),
                    (Permissions.Signs, RuleMode.Allow),
                ),
                "p",
            ),
            RoleState(
                everyoneUUID,
                "Everyone",
                true,
                Map(
                ),
                "u",
            ),
        )
        var ord = ""
        for {
            _ <- GroupStates(newGroupID, name).save()
            _ <- GroupMemberships(newGroupID, owner).save()
            _ <- GroupOwnerships(newGroupID, owner).save()
            _ <- roles.traverse { role =>
                ord = Lexorank.rank(ord, "")
                GroupRoles(
                    newGroupID,
                    role.id,
                    role.name,
                    role.hoist,
                    role.permissions,
                    ord,
                ).save()
            }
        } yield newGroupID

    def leaveGroup(as: UserID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[GroupError, Unit]] =
        getGroup(group)
            .guard(GroupError.MustNotBeOwner) {
                !_.owners.contains(as)
            }
            .guard(GroupError.TargetNotInGroup) {
                _.users.contains(as)
            }
            .flatMap { _ =>
                EitherT.right(
                    sql.commandIO(
                        sql"DELETE FROM GroupMemberships WHERE GroupID = $uuid AND UserID = $uuid",
                        (group, as),
                    )
                )
            }
            .map(_ => ())
            .value

    def kickUserFromGroup(as: UserID, target: UserID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[GroupError, Unit]] =
        getGroup(group)
            .guard(GroupError.MustBeInGroup) {
                _.users.contains(as)
            }
            .guard(GroupError.TargetNotInGroup) {
                _.users.contains(target)
            }
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.RemoveUser, as, nullUUID)
            }
            .guard(GroupError.TargetIsAboveYou) {
                !_.owners.contains(target)
            }
            .guard(GroupError.TargetIsAboveYou) { group =>
                if group.owners.contains(as) then true
                else
                    val ids = group.roles.map(_.id)
                    val maxTarget =
                        group.users(target).map(ids.indexOf).maxOption
                    val maxSelf = group.users(as).map(ids.indexOf).maxOption
                    maxTarget < maxSelf
            }
            .flatMap { _ =>
                EitherT.right(
                    sql.commandIO(
                        sql"DELETE FROM GroupMemberships WHERE GroupID = $uuid AND UserID = $uuid",
                        (group, target),
                    )
                )
            }
            .map(_ => ())
            .value

    def deleteGroup(as: UserID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, Unit] =
        EitherT
            .right(getGroupOwners(group))
            .guard(GroupError.MustBeOwner) {
                _.view.map(_.userID).contains(as)
            }
            .guard(GroupError.MustBeOwner) {
                _.view.map(_.userID).contains(as)
            }
            .guard(GroupError.MustBeOnlyOwner) {
                _.length == 1
            }
            .flatMap { _ =>
                EitherT.right(
                    sql.commandIO(
                        sql"DELETE FROM GroupStates WHERE ID = $uuid;",
                        group,
                    )
                )
            }
            .map(_ => ())

    private def getGroupOwners(
        group: GroupID
    )(using s: Session[IO]): IO[List[GroupOwnerships]] =
        sql.queryListIO(
            sql"SELECT GroupID, UserID FROM GroupOwnerships WHERE GroupID = $uuid;",
            (uuid *: uuid).to[GroupOwnerships],
            group,
        )

    private def getGroupMembers(
        group: GroupID
    )(using s: Session[IO]): IO[List[GroupMemberships]] =
        sql.queryListIO(
            sql"SELECT GroupID, UserID FROM GroupMemberships WHERE GroupID = $uuid;",
            (uuid *: uuid).to[GroupMemberships],
            group,
        )

    private def getSubgroups(
        group: GroupID
    )(using Session[IO], Transaction[IO]): IO[List[SubgroupState]] =
        sql
            .queryListIO(
                sql"""
        SELECT SubgroupID, Name FROM Subgroups WHERE ParentGroup = $uuid
        """,
                uuid *: text,
                group,
            )
            .flatMap { it =>
                it.traverse { tuple =>
                    val (sgid, name) = tuple

                    sql
                        .queryListIO(
                            sql"""
                    SELECT RoleID, Permissions FROM SubgroupPermissions WHERE SubgroupID = $uuid
                    """,
                            uuid *: jsonb[Map[Permissions, RuleMode]],
                            sgid,
                        )
                        .map { roles =>
                            SubgroupState(sgid, name, roles.toMap)
                        }
                }
            }

    private def groupStateIO(group: GroupID)(using
        s: Session[IO]
    ): EitherT[IO, GroupError, GroupStates] =
        EitherT.fromOptionF(
            sql.queryOptionIO(
                sql"""
        SELECT ID, Name FROM GroupStates WHERE ID = $uuid;
        """,
                (uuid *: text).to[GroupStates],
                group,
            ),
            GroupError.GroupNotFound,
        )

    private def rolesIO(
        group: GroupID
    )(using s: Session[IO]): IO[List[RoleState]] =
        sql
            .queryListIO(
                sql"""
        SELECT GroupID, RoleID, Name, Hoist, Permissions, Ord FROM GroupRoles WHERE GroupID = $uuid;
        """,
                (uuid *: uuid *: text *: bool *: jsonb[
                    Map[Permissions, RuleMode]
                ] *: text).to[GroupRoles],
                group,
            )
            .map { list =>
                list.map { role =>
                    RoleState(
                        id = role.roleID,
                        name = role.name,
                        hoist = role.hoist,
                        permissions = role.permissions,
                        ord = role.ord,
                    )
                }.sortWith((a, b) => a.ord.compareTo(b.ord) < 0)
            }

    private def getAll(
        group: GroupID
    )(using Session[IO], Transaction[IO]): EitherT[IO, GroupError, GroupState] =
        for {
            gs <- groupStateIO(group)
            gm <- EitherT.right(
                sql.queryListIO(
                    sql"""
            SELECT GroupID, UserID FROM GroupMemberships WHERE GroupID = $uuid;
            """,
                    (uuid *: uuid).to[GroupMemberships],
                    group,
                )
            )
            gr <- EitherT.right(rolesIO(group))
            grm <- EitherT.right(
                sql.queryListIO(
                    sql"""
            SELECT GroupID, RoleID, UserID FROM GroupRoleMemberships WHERE GroupID = $uuid;
            """,
                    (uuid *: uuid *: uuid).to[GroupRoleMemberships],
                    group,
                )
            )
            gro <- EitherT.right(
                sql.queryListIO(
                    sql"""
            SELECT GroupID, UserID FROM GroupOwnerships WHERE GroupID = $uuid;
            """,
                    (uuid *: uuid).to[GroupOwnerships],
                    group,
                )
            )
            subgroups <- EitherT.right(getSubgroups(group))
        } yield GroupState(
            metadata = gs,
            owners = gro.map(_.userID),
            roles = gr,
            users = gm.map { membership =>
                (
                    membership.userID,
                    grm.filter(_.userID == membership.userID)
                        .map(_.roleID)
                        .toSet,
                )
            }.toMap,
            subgroups = subgroups,
        )

    def promoteToOwner(as: UserID, target: UserID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, Unit] =
        (for {
            owners <- EitherT.right(getGroupOwners(group))
            members <- EitherT.right(getGroupMembers(group))
        } yield (owners, members))
            .guard(GroupError.MustBeOwner) {
                _._1.view.map(_.userID).contains(as)
            }
            .guard(GroupError.TargetNotInGroup) {
                _._2.view.map(_.userID).contains(target)
            }
            .guard(GroupError.TargetIsAlreadyOwner) {
                !_._1.view.map(_.userID).contains(target)
            }
            .flatMap { _ =>
                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                INSERT INTO GroupOwnerships (GroupID, UserID) VALUES ($uuid, $uuid);
                """,
                            (group, target),
                        )
                        .map(_ => ())
                )
            }

    def giveUpOwnership(as: UserID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, Unit] =
        EitherT
            .right(getGroupOwners(group))
            .guard(GroupError.MustBeOwner) {
                _.exists(_.userID == as)
            }
            .guard(GroupError.GroupWouldHaveNoOwners) {
                _.length > 1
            }
            .flatMap { _ =>
                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                DELETE FROM GroupOwnerships WHERE GroupID = $uuid AND UserID = $uuid
                """,
                            (group, as),
                        )
                        .map(_ => ())
                )
            }

    def sudoSetRolePermissions(
        groupID: GroupID,
        roleID: RoleID,
        permissions: Map[Permissions, RuleMode],
    )(using s: Session[IO]): IO[Completion] =
        sql.commandIO(
            sql"""
        UPDATE GroupRoles SET Permissions = $jsonb WHERE GroupID = $uuid AND RoleID = $uuid
        """,
            (permissions.asJson, groupID, roleID),
        )

    def setRolePermissions(
        as: UserID,
        groupID: GroupID,
        roleID: RoleID,
        permissions: Map[Permissions, RuleMode],
    )(using Session[IO], Transaction[IO]): EitherT[IO, GroupError, Unit] =
        getAll(groupID)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageRoles, as, nullUUID)
            }
            .guard(GroupError.RoleNotFound) {
                _.roles.exists(_.id == roleID)
            }
            .guardRoleAboveYours(as, roleID)
            .guard(GroupError.MustHavePermission) { gs =>
                permissions.forall { (perm, mode) =>
                    if mode == RuleMode.Allow then gs.check(perm, as, nullUUID)
                    else true
                }
            }
            .flatMap { _ =>
                EitherT.right(
                    sudoSetRolePermissions(groupID, roleID, permissions)
                        .map(_ => ())
                )
            }

    def setSubgroupRolePermissions(
        as: UserID,
        groupID: GroupID,
        subgroupID: SubgroupID,
        roleID: RoleID,
        permissions: Map[Permissions, RuleMode],
    )(using Session[IO], Transaction[IO]): EitherT[IO, GroupError, Unit] =
        getAll(groupID)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageSubgroups, as, subgroupID)
            }
            .guard(GroupError.RoleNotFound) {
                _.roles.exists(_.id == roleID)
            }
            .guardRoleAboveYours(as, roleID)
            .guard(GroupError.MustHavePermission) { gs =>
                permissions.forall { (perm, mode) =>
                    if mode == RuleMode.Allow then
                        gs.check(perm, as, subgroupID)
                    else true
                }
            }
            .flatMap { _ =>
                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                INSERT INTO SubgroupPermissions (
                    GroupID, SubgroupID, RoleID, Permissions
                ) VALUES (
                    $uuid, $uuid, $uuid, $jsonb
                ) ON CONFLICT (SubgroupID, RoleID) DO UPDATE SET
                    Permissions = EXCLUDED.Permissions;
                """,
                            (
                                groupID,
                                subgroupID,
                                roleID,
                                permissions.asJson,
                            ),
                        )
                        .map(_ => ())
                )
            }

    def roles(
        group: GroupID
    )(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, List[RoleState]] =
        for {
            gs <- groupStateIO(group)
            roles <- EitherT.right(rolesIO(group))
        } yield roles

    def createRole(as: UserID, group: GroupID, name: String)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, RoleID] =
        getAll(group)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageRoles, as, nullUUID)
            }
            .flatMap { group =>
                val rid = ju.UUID.randomUUID()
                val prevOrd = group.roles.find(_.id == groupMemberUUID).get.ord
                val ord = group.roles
                    .filterNot(x =>
                        x.id == everyoneUUID || x.id == groupMemberUUID
                    )
                    .lastOption
                    .map { x =>
                        Lexorank.rank(x.ord, prevOrd)
                    }
                    .getOrElse {
                        Lexorank.rank(prevOrd, "")
                    }

                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                INSERT INTO GroupRoles (
                    GroupID, RoleID, Name, Hoist, Permissions, Ord
                ) VALUES (
                    $uuid, $uuid, $text, $bool, $jsonb, $text
                );
                """,
                            (
                                group.metadata.id,
                                rid,
                                name,
                                false,
                                Map[Permissions, RuleMode]().asJson,
                                ord,
                            ),
                        )
                        .map(_ => rid)
                )
            }

    def createSubgroup(as: UserID, group: GroupID, name: String)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, SubgroupID] =
        getAll(group)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageSubgroups, as, nullUUID)
            }
            .flatMap { group =>
                val sgid = ju.UUID.randomUUID()

                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                INSERT INTO Subgroups (
                    ParentGroup, SubgroupID, Name
                ) VALUES (
                    $uuid, $uuid, $text
                );
                """,
                            (group.metadata.id, sgid, name),
                        )
                        .map(_ => sgid)
                )
            }

    def deleteSubgroup(as: UserID, group: GroupID, subgroup: SubgroupID)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, Unit] =
        getAll(group)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageSubgroups, as, nullUUID)
            }
            .flatMap { group =>
                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                DELETE FROM Subgroups WHERE SubgroupID = $uuid;
                """,
                            subgroup,
                        )
                        .map(_ => ())
                )
            }

    def deleteRole(as: UserID, role: RoleID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, Unit] =
        getAll(group)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageRoles, as, nullUUID)
            }
            .guard(GroupError.RoleNotFound) {
                _.roles.exists(_.id == role)
            }
            .guard(GroupError.CantAssignEveryone) { _ =>
                everyoneUUID != role && groupMemberUUID != role
            }
            .guardRoleAboveYours(as, role)
            .flatMap { _ =>
                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                DELETE FROM GroupRoles WHERE GroupID = $uuid AND RoleID = $uuid;
                """,
                            (group, role),
                        )
                        .map(_ => ())
                )
            }

    def assignRole(
        as: UserID,
        target: UserID,
        group: GroupID,
        role: RoleID,
        has: Boolean,
    )(using Session[IO], Transaction[IO]): EitherT[IO, GroupError, Unit] =
        getAll(group)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageUserRoles, as, nullUUID)
            }
            .guard(GroupError.RoleNotFound) {
                _.roles.exists(_.id == role)
            }
            .guard(GroupError.TargetNotInGroup) {
                _.users.contains(target)
            }
            .guard(GroupError.CantAssignEveryone) { _ =>
                everyoneUUID != role && groupMemberUUID != role
            }
            .guardRoleAboveYours(as, role)
            .flatMap { _ =>
                EitherT
                    .right(
                        if has then
                            sql.commandIO(
                                sql"""
                        INSERT INTO GroupRoleMemberships (GroupID, RoleID, UserID) VALUES ($uuid, $uuid, $uuid);
                        """,
                                (group, role, target),
                            )
                        else
                            sql.commandIO(
                                sql"""
                        DELETE FROM GroupRoleMemberships WHERE GroupID = $uuid AND RoleID = $uuid AND UserID = $uuid;
                        """,
                                (group, role, target),
                            )
                    )
                    .map(_ => ())
            }

    // TODO: invites/passwords
    def addToGroup(user: UserID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, Unit] =
        EitherT
            .right(getGroupMembers(group))
            .guard(GroupError.AlreadyInGroup) { members =>
                !members.view.map(_.userID).contains(user)
            }
            .flatMap { _ =>
                EitherT.right(
                    sql
                        .commandIO(
                            sql"""
                INSERT INTO GroupMemberships (GroupID, UserID) VALUES ($uuid, $uuid);
                """,
                            (group, user),
                        )
                        .map(_ => ())
                )
            }

    // BUKKIT ONLY CURRENTLY
    def groupAudience(
        groupID: GroupID
    )(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, (String, Audience)] =
        for {
            gs <- groupStateIO(groupID)
            members <- EitherT.right(
                getGroupMembers(groupID).map(_.map(_.userID))
            )
        } yield (
            gs.name,
            Audience.audience(
                Bukkit.getServer.getOnlinePlayers.asScala
                    .filter(plr => members.contains(plr.getUniqueId))
                    .asJava
            ),
        )

    def userGroups(
        userID: UserID
    )(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, List[GroupStates]] =
        EitherT.right(
            sql.queryListIO(
                sql"""
        SELECT
            GroupStates.*
        FROM
            GroupStates, GroupMemberships
        WHERE
            GroupMemberships.UserID = $uuid
            AND GroupStates.ID = GroupMemberships.GroupID;
        """,
                (uuid *: text).to[GroupStates],
                userID,
            )
        )

    def setSubclaims(
        as: UserID,
        group: GroupID,
        subgroup: SubgroupID,
        claims: Subclaims,
    )(using Session[IO], Transaction[IO]): IO[Either[GroupError, Unit]] =
        getAll(group)
            .guard(GroupError.NoPermissions) {
                _.check(Permissions.ManageClaims, as, subgroup)
            }
            .flatMap { _ =>
                EitherT.right(
                    sql.commandIO(
                        sql"""
                INSERT INTO SubgroupClaims (
                    GroupID, SubgroupID, Claims
                ) VALUES (
                    $uuid, $uuid, $jsonb
                ) ON CONFLICT (SubgroupID) DO UPDATE SET Claims = EXCLUDED.Claims;
                """,
                        (group, subgroup, claims.asJson),
                    )
                )
            }
            .map(_ => ())
            .value

    /// relatively heavy function, call once and cache only if you need to frequently consult permissions
    def getSubclaims(
        groupID: GroupID
    )(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[GroupError, Map[SubgroupID, Subclaims]]] =
        EitherT
            .right(
                sql.queryListIO(
                    sql"""
        SELECT SubgroupID, Claims FROM SubgroupClaims WHERE GroupID = $uuid;
        """,
                    uuid *: jsonb[Subclaims],
                    groupID,
                )
            )
            .map { x => x.toMap }
            .value

    def getGroup(groupID: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): EitherT[IO, GroupError, GroupState] =
        getAll(groupID)

    def check(
        user: UserID,
        group: GroupID,
        subgroup: SubgroupID,
        permission: Permissions,
    )(using Session[IO], Transaction[IO]): EitherT[IO, GroupError, Boolean] =
        getAll(group).map(_.check(permission, user, subgroup))

    def checkE(
        user: UserID,
        group: GroupID,
        subgroup: SubgroupID,
        permission: Permissions,
    )(using Session[IO], Transaction[IO]): EitherT[IO, GroupError, Unit] =
        getAll(group)
            .guard(GroupError.NoPermissions) {
                _.check(permission, user, subgroup)
            }
            .map { _ => () }
