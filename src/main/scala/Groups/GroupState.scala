// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import java.{util => ju}
import io.circe._, io.circe.generic.semiauto._
import org.bukkit.Material

type UserID = ju.UUID
type RoleID = ju.UUID
type GroupID = ju.UUID
type SubgroupID = ju.UUID

/** null UUID for subgroups */
lazy val nullUUID = ju.UUID(0, 0)
/** the null UUID is used for the role that everyone in the world has, no exceptions */
lazy val everyoneUUID = ju.UUID(0, 0)
/** the null UUID is used for the role that everyone in a group has, no exceptions */
lazy val groupMemberUUID = ju.UUID(0, 1)

enum RuleMode:
    case Allow, Deny

    def toggle(): RuleMode =
        this match
            case Allow => Deny
            case Deny => Allow

implicit val rmDecoder: Decoder[RuleMode] = deriveDecoder[RuleMode]
implicit val rmEncoder: Encoder[RuleMode] = deriveEncoder[RuleMode]

enum Permissions(val name: String):
    case ManageRoles extends Permissions("roles.manage")
    case ManageUserRoles extends Permissions("roles.user.manage")
    case InviteUser extends Permissions("users.invite")
    case RemoveUser extends Permissions("users.manage.remove")
    case UpdateGroupInformation extends Permissions("group.manage")
    case ManageClaims extends Permissions("claims.manage")

    case AddReinforcements extends Permissions("reinforcements.add")
    case RemoveReinforcements extends Permissions("reinforcements.remove")

    case Build extends Permissions("build")
    case Chests extends Permissions("chests")
    case Doors extends Permissions("doors")
    case Crops extends Permissions("crops")

    case Entities extends Permissions("entities")

    def displayName(): String =
        this match
            case ManageRoles => "Manage Roles"
            case ManageUserRoles => "Assign Roles"
            case InviteUser => "Invite Users"
            case RemoveUser => "Remove Users"
            case UpdateGroupInformation => "Modify Group Information"
            case ManageClaims => "Manage Claims"
            case AddReinforcements => "Reinforce Blocks/Entities"
            case RemoveReinforcements => "Unreinforce Blocks/Entities"
            case Build => "Build"
            case Chests => "Use Chests"
            case Doors => "Use Doors"
            case Crops => "Use Crops"
            case Entities => "Interact with Entities"

    def displayExplanation(): String =
        this match
            case ManageRoles => "Allows users to modify roles"
            case ManageUserRoles => "Allows users to assign and revoke roles below their highest role to other users"
            case InviteUser => "Allows user to invite others"
            case RemoveUser => "Allows user to remove others"
            case UpdateGroupInformation => "Allows user to update group information"
            case AddReinforcements => "Allows the user to reinforce blocks and entities"
            case RemoveReinforcements => "Allows the user to unreinforce blocks and entities"
            case Build => "Allows the user to modify reinforced blocks"
            case Chests => "Allows the user to open reinforced chests"
            case Doors => "Allows the user to open reinforced doors"
            case Crops => "Allows the user to plant and harvest crops on reinforced farmland"
            case Entities => "Allows the user to interact with entities"
            case ManageClaims => "Allows users to manage the claims of this group's beacons"

    def displayItem(): Material =
        this match
            case ManageRoles => Material.LEATHER_CHESTPLATE
            case ManageUserRoles => Material.IRON_CHESTPLATE
            case InviteUser => Material.PLAYER_HEAD
            case RemoveUser => Material.BARRIER
            case UpdateGroupInformation => Material.NAME_TAG
            case AddReinforcements => Material.STONE
            case RemoveReinforcements => Material.IRON_PICKAXE
            case Build => Material.BRICKS
            case Chests => Material.CHEST
            case Doors => Material.OAK_DOOR
            case Crops => Material.WHEAT
            case Entities => Material.EGG
            case ManageClaims => Material.BEACON

implicit val pKeyEncoder: KeyEncoder[Permissions] = new KeyEncoder[Permissions]:
    override def apply(perm: Permissions): String =
        perm.name
implicit val pKeyDecoder: KeyDecoder[Permissions] = new KeyDecoder[Permissions]:
    override def apply(key: String): Option[Permissions] =
        Permissions.values.find(v => v.name == key)

case class SubgroupState(
    id: SubgroupID,
    name: String,
    permissions: Map[RoleID, Map[Permissions, RuleMode]],
)

case class RoleState(
    id: RoleID,
    name: String,
    hoist: Boolean,
    permissions: Map[Permissions, RuleMode],
)

implicit val rsDecoder: Decoder[RoleState] = deriveDecoder[RoleState]
implicit val rsEncoder: Encoder[RoleState] = deriveEncoder[RoleState]

/** This holds all the information about a group */
case class GroupState(
    metadata: GroupStates,
    owners: List[UserID],
    roles: List[RoleState],
    users: Map[UserID, Set[RoleID]],
    subgroups: List[SubgroupState],
):
    private def subgroupPermissionsFor(subgroup: SubgroupID, role: RoleID): Map[Permissions, RuleMode] =
        subgroups.find(_.id == subgroup)
            .flatMap(_.permissions.get(role))
            .getOrElse(Map())
    private def permissionsFor(role: RoleID): Map[Permissions, RuleMode] =
        roles.find { x => x.id == role }
            .map(_.permissions)
            .getOrElse(Map())

    def check(perm: Permissions, user: UserID, subgroup: SubgroupID): Boolean =
        if owners.contains(user) then
            true
        else
            val userRoles = users.get(user).map(_.toList.appended(groupMemberUUID)).getOrElse(List()).appended(everyoneUUID)
            val userRolesSorted = userRoles.sortBy(roles.indexOf(_))
            val perms = userRolesSorted.view.map { role => permissionsFor(role).get(perm) }

            if subgroup != nullUUID then
                val subgroupPerms = userRolesSorted.view.map { role => subgroupPermissionsFor(subgroup, role).get(perm) }
                subgroupPerms.find { x => x.isDefined }.flatten match
                    case Some(RuleMode.Allow) => return true
                    case Some(RuleMode.Deny) => return false
                    case _ =>

            perms.find { x => x.isDefined }.flatten match
                case Some(RuleMode.Allow) => true
                case Some(RuleMode.Deny) => false
                case _ => false
