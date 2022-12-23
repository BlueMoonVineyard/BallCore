// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.Storage
import BallCore.Groups.Extensions._
import java.{util => ju}

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
class GroupManager()(using kvs: Storage.KeyVal, gsm: GroupStateManager):
    private def get(group: GroupID): Either[GroupError, GroupState] =
        gsm.get(group) match
            case Some(value) => Right(value)
            case None => Left(GroupError.GroupNotFound)
    def createGroup(owner: UserID, name: String): GroupID =
        val owners = List(owner)
        val users: Map[UserID, Set[RoleID]] = Map((owner, Set(nullUUID)))
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
                    (Permissions.AddReinforcements, RuleMode.Allow),
                    (Permissions.RemoveReinforcements, RuleMode.Allow),
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
        get(group)
         .guard(GroupError.MustBeOwner) { _.owners.contains(as) }
         .guard(GroupError.MustBeOnlyOwner) { _.owners.length == 1 }
         .map { _ =>
            gsm.remove(group)
        }

    def promoteToOwner(as: UserID, target: UserID, group: GroupID): Either[GroupError, Unit] =
        get(group)
         .guard(GroupError.MustBeOwner) { _.owners.contains(as) }
         .guard(GroupError.TargetNotInGroup) { _.users.contains(target) }
         .guard(GroupError.TargetIsAlreadyOwner) { !_.owners.contains(target) }
         .map { data =>
            data.copy(owners = data.owners.appended(target))
        }.map { data =>
            gsm.set(group, data)
        }

    def giveUpOwnership(as: UserID, group: GroupID): Either[GroupError, Unit] =
        get(group)
         .guard(GroupError.MustBeOwner) { _.owners.contains(as) }
         .guard(GroupError.GroupWouldHaveNoOwners) { _.owners.length > 1 }
         .map { data =>
            data.copy(owners = data.owners.filterNot(x => x == as))
        }.map { data =>
            gsm.set(group, data)
        }

    def roles(group: GroupID): Either[GroupError, List[RoleState]] =
        get(group).map(_.roles)

    def assignRole(as: UserID, target: UserID, group: GroupID, role: RoleID, has: Boolean): Either[GroupError, Unit] =
        get(group)
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
            val neue = if has then
                data.users(target).incl(role)
            else
                data.users(target).excl(role)
            data.copy(users = data.users.updated(target, neue))
        }.map { data =>
            gsm.set(group, data)
        }

    // TODO: invites/passwords
    def addToGroup(user: UserID, group: GroupID): Either[GroupError, Unit] =
        get(group)
         .guard(GroupError.AlreadyInGroup) { !_.users.contains(user) }
         .map { data =>
            data.copy(users = data.users + (user -> Set(nullUUID)))
        }.map { data =>
            gsm.set(group, data)
        }

    def check(user: UserID, group: GroupID, permission: Permissions): Either[GroupError, Boolean] =
        get(group).map(_.check(permission, user))

    def checkE(user: UserID, group: GroupID, permission: Permissions): Either[GroupError, Unit] =
        get(group).guard(GroupError.NoPermissions) { _.check(permission, user) }.map { _ => () }