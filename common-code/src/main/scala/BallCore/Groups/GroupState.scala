// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import io.circe.*
import io.circe.generic.semiauto.*

import java.util as ju

type UserID = ju.UUID
type RoleID = ju.UUID
type GroupID = ju.UUID
type SubgroupID = ju.UUID

/** null UUID for subgroups */
lazy val nullUUID = ju.UUID(0, 0)

/** the null UUID is used for the role that everyone in the world has, no
  * exceptions
  */
lazy val everyoneUUID = ju.UUID(0, 0)

/** the null UUID is used for the role that everyone in a group has, no
  * exceptions
  */
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
    case ManageSubgroups extends Permissions("subgroups.manage")
    case ManageClaims extends Permissions("claims.manage")

    case AddReinforcements extends Permissions("reinforcements.add")
    case RemoveReinforcements extends Permissions("reinforcements.remove")

    case Build extends Permissions("build")
    case Chests extends Permissions("chests")
    case Doors extends Permissions("doors")
    case Crops extends Permissions("crops")
    case Signs extends Permissions("signs")

    case Entities extends Permissions("entities")

    def displayName(): String =
        this match
            case ManageRoles => "Manage Roles"
            case ManageUserRoles => "Assign Roles"
            case InviteUser => "Invite Users"
            case RemoveUser => "Remove Users"
            case UpdateGroupInformation => "Modify Group Information"
            case ManageSubgroups => "Manage Subgroups"
            case ManageClaims => "Manage Claims"
            case AddReinforcements => "Reinforce Blocks/Entities"
            case RemoveReinforcements => "Unreinforce Blocks/Entities"
            case Build => "Build"
            case Chests => "Use Chests"
            case Doors => "Use Doors"
            case Crops => "Use Crops"
            case Signs => "Edit Signs"
            case Entities => "Interact with Entities"

    def displayExplanation(): String =
        this match
            case ManageRoles => "Allows users to modify roles"
            case ManageUserRoles =>
                "Allows users to assign and revoke roles below their highest role to other users"
            case InviteUser => "Allows user to invite others"
            case RemoveUser => "Allows user to remove others"
            case UpdateGroupInformation =>
                "Allows user to update group information"
            case AddReinforcements =>
                "Allows the user to reinforce blocks and entities"
            case RemoveReinforcements =>
                "Allows the user to unreinforce blocks and entities"
            case Build => "Allows the user to modify reinforced blocks"
            case Chests => "Allows the user to open reinforced chests"
            case Doors => "Allows the user to open reinforced doors"
            case Crops =>
                "Allows the user to plant and harvest crops on reinforced farmland"
            case Signs => "Allows users to edit signs (hanging and not)"
            case Entities => "Allows the user to interact with entities"
            case ManageSubgroups =>
                "Allows users to create, rename, and delete subgroups"
            case ManageClaims =>
                "Allows users to manage the claims of this group's beacons"

implicit val pKeyEncoder: KeyEncoder[Permissions] = (perm: Permissions) =>
    perm.name
implicit val pKeyDecoder: KeyDecoder[Permissions] = (key: String) =>
    Permissions.values.find(v => v.name == key)

case class SubgroupState(
    id: SubgroupID,
    name: String,
    permissions: Map[RoleID, Map[Permissions, RuleMode]],
)

case class Position(
    x: Int,
    y: Int,
    z: Int,
    world: ju.UUID,
)

implicit val pDecoder: Decoder[Position] = deriveDecoder[Position]
implicit val pEncoder: Encoder[Position] = deriveEncoder[Position]

case class Volume(
    cornerA: Position,
    cornerB: Position,
):
    private def cornerALocation(): Position =
        cornerA

    private def cornerBLocation(): Position =
        cornerB.copy(x = cornerB.x + 1, y = cornerB.y + 1, z = cornerB.z + 1)

    private def check1D(target: Double, a1: Double, a2: Double): Boolean =
        if !(a1 <= a2) then
            throw IllegalArgumentException("a1 must be lower than a2")

        if a1 <= target && target <= a2 then true
        else false

    def contains(target: Position): Boolean =
        val ca = cornerALocation()
        val cb = cornerBLocation()

        check1D(target.x, ca.x, cb.x) &&
        check1D(target.y, ca.y, cb.y) &&
        check1D(target.z, ca.z, cb.z) &&
        ca.world == target.world

implicit val vDecoder: Decoder[Volume] = deriveDecoder[Volume]
implicit val vEncoder: Encoder[Volume] = deriveEncoder[Volume]

case class Subclaims(
    volumes: List[Volume]
):
    def contains(target: Position): Boolean =
        volumes.exists(_.contains(target))

implicit val scDecoder: Decoder[Subclaims] = deriveDecoder[Subclaims]
implicit val scEncoder: Encoder[Subclaims] = deriveEncoder[Subclaims]

case class RoleState(
    id: RoleID,
    name: String,
    hoist: Boolean,
    permissions: Map[Permissions, RuleMode],
    ord: String,
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
    private def subgroupPermissionsFor(
        subgroup: SubgroupID,
        role: RoleID,
    ): Map[Permissions, RuleMode] =
        subgroups
            .find(_.id == subgroup)
            .flatMap(_.permissions.get(role))
            .getOrElse(Map())

    private def permissionsFor(role: RoleID): Map[Permissions, RuleMode] =
        roles
            .find { x => x.id == role }
            .map(_.permissions)
            .getOrElse(Map())

    def check(perm: Permissions, user: UserID, subgroup: SubgroupID): Boolean =
        if owners.contains(user) then true
        else
            val userRoles = users
                .get(user)
                .map(_.toList.appended(groupMemberUUID))
                .getOrElse(List())
                .appended(everyoneUUID)
            val userRolesSorted = userRoles.sortBy(roles.indexOf(_))
            val perms = userRolesSorted.view.map { role =>
                permissionsFor(role).get(perm)
            }

            if subgroup != nullUUID then
                val subgroupPerms = userRolesSorted.view.map { role =>
                    subgroupPermissionsFor(subgroup, role).get(perm)
                }
                subgroupPerms.find { x => x.isDefined }.flatten match
                    case Some(RuleMode.Allow) => return true
                    case Some(RuleMode.Deny) => return false
                    case _ =>

            perms.find { x => x.isDefined }.flatten match
                case Some(RuleMode.Allow) => true
                case Some(RuleMode.Deny) => false
                case _ => false
