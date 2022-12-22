// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.Storage
import java.{util => ju}

enum GroupError:
    case MustBeOwner
    case MustBeOnlyOwner
    case GroupNotFound
    case TargetNotInGroup
    case GroupWouldHaveNoOwners
    case TargetIsAlreadyOwner
    case AlreadyInGroup

/** The GroupManager implements all of the logic relating to group management and permission */
class GroupManager()(using kvs: Storage.KeyVal, gsm: GroupStateManager):
    private def get(group: GroupID): Either[GroupError, GroupState] =
        gsm.get(group) match
            case Some(value) => Right(value)
            case None => Left(GroupError.GroupNotFound)
    def createGroup(owner: UserID, name: String): GroupID =
        val owners = List(owner)
        val users: Map[UserID, List[RoleID]] = Map((owner, List()))
        val permissions: Map[RoleID, Map[Permissions, RuleMode]] = Map()
        val roles: List[RoleState] = List(
            RoleState(
                ju.UUID.randomUUID(),
                "Admin", true,
                Map(
                    (Permissions.SetRolePermissions, RuleMode.Allow),
                    (Permissions.GetRolePermissions, RuleMode.Allow),
                    (Permissions.ManageRoles, RuleMode.Allow),
                    (Permissions.ManageUserRoles, RuleMode.Allow),
                    (Permissions.InviteUser, RuleMode.Allow),
                    (Permissions.RemoveUser, RuleMode.Allow),
                    (Permissions.UpdateGroupInformation, RuleMode.Allow),
                ),
            ),
            RoleState(
                ju.UUID.randomUUID(),
                "Moderator", true,
                Map(
                    (Permissions.GetRolePermissions, RuleMode.Allow),
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
        val state = GroupState(name, owners, roles, users)
        val gid = ju.UUID.randomUUID()
        gsm.set(gid, state)
        gid

    def deleteGroup(as: UserID, group: GroupID): Either[GroupError, Unit] =
        get(group).flatMap { data =>
            if !data.owners.contains(as) then
                Left(GroupError.MustBeOwner)
            else
                Right(data)
        }.flatMap { data =>
            if data.owners.length != 1 then
                Left(GroupError.MustBeOnlyOwner)
            else
                Right(data)
        }.map { _ =>
            gsm.remove(group)
        }

    def promoteToOwner(as: UserID, target: UserID, group: GroupID): Either[GroupError, Unit] =
        get(group).flatMap { data =>
            if !data.owners.contains(as) then
                Left(GroupError.MustBeOwner)
            else
                Right(data)
        }.flatMap { data =>
            if !data.users.contains(target) then
                Left(GroupError.TargetNotInGroup)
            else
                Right(data)
        }.flatMap { data =>
            if !data.users.contains(target) then
                Left(GroupError.TargetIsAlreadyOwner)
            else
                Right(data)
        }.map { data =>
            data.copy(owners = data.owners.appended(target))
        }.map { data =>
            gsm.set(group, data)
        }

    def giveUpOwnership(as: UserID, group: GroupID): Either[GroupError, Unit] =
        get(group).flatMap { data =>
            if !data.owners.contains(as) then
                Left(GroupError.MustBeOwner)
            else
                Right(data)
        }.flatMap { data =>
            if data.owners.length == 1 then
                Left(GroupError.GroupWouldHaveNoOwners)
            else
                Right(data)
        }.map { data =>
            data.copy(owners = data.owners.filterNot(x => x == as))
        }.map { data =>
            gsm.set(group, data)
        }

    // TODO: invites/passwords
    def addToGroup(user: UserID, group: GroupID): Either[GroupError, Unit] =
        get(group).flatMap { data =>
            if data.users.contains(user) then
                Left(GroupError.AlreadyInGroup)
            else
                Right(data)
        }.map { data =>
            data.copy(users = data.users + (user -> List(nullUUID)))
        }.map { data =>
            gsm.set(group, data)
        }