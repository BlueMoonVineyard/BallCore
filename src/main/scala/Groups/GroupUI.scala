// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.UI.callback
import BallCore.UI.Elements._
import scala.xml.Elem
import org.bukkit.entity.HumanEntity
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import BallCore.UI.Prompts
import org.bukkit.entity.Player
import scala.concurrent.ExecutionContext
import BallCore.UI.Accumulator
import org.bukkit.Bukkit
import BallCore.UI.UIProgram
import BallCore.UI.UIServices
import scala.concurrent.Future

enum GroupManagementMessage:
    case ViewMembers
    case ViewRoles
    case InviteMember
    case ClickRole(role: RoleID)

class GroupManagementProgram(using gm: GroupManager) extends UIProgram:
    import io.circe.generic.auto._

    case class Flags(groupID: GroupID, userID: UserID)
    case class Model(group: GroupState, userID: UserID, viewing: ViewingWhat)
    enum ViewingWhat:
        case Players
        case Roles
    type Message = GroupManagementMessage

    override def init(flags: Flags): Model =
        val group = gm.getGroup(flags.groupID).toOption.get
        Model(group, flags.userID, ViewingWhat.Players)
    override def view(model: Model): Elem =
        val group = model.group
        Root(s"Viewing ${group.metadata.name}", 6) {
            OutlinePane(0, 0, 1, 6) {
                Button("player_head", "§aMembers", GroupManagementMessage.ViewMembers)()
                if group.check(Permissions.ManageRoles, model.userID) then
                    Button("writable_book", "§aManage Roles", GroupManagementMessage.ViewRoles)()
                if group.check(Permissions.InviteUser, model.userID) then
                    Button("compass", "§aInvite A Member", GroupManagementMessage.InviteMember)()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            model.viewing match
                case ViewingWhat.Players => Players(model)
                case ViewingWhat.Roles => Roles(model)
        }
    def Players(model: Model)(using Accumulator): Unit =
        val group = model.group
        OutlinePane(2, 0, 7, 6) {
            group.users.keys.toList.map(x => Bukkit.getOfflinePlayer(x)).sortBy(_.getName()).foreach { x =>
                Item("player_head", displayName = Some(s"§r${x.getName()}")) {
                    SkullUsername(x.getName())
                    if group.owners.contains(x.getUniqueId()) then
                        Lore("§a§lOwner")
                    val roles = group.roles
                        .filter(r => group.users(x.getUniqueId()).contains(r.id))
                        .filterNot(_.id == nullUUID)
                    if roles.length > 0 then
                        Lore("")
                        Lore("§r§f§nRoles")
                        roles.foreach { x =>
                            Lore(s"§r§f- ${x.name}")
                        }
                }
            }
        }
    def Roles(model: Model)(using Accumulator): Unit =
        val group = model.group
        OutlinePane(2, 0, 7, 6) {
            group.roles.foreach { x =>
                // Item("leather_chestplate", displayName = Some(s"§r§f${x.name}"), onClick = callback(clickRole)) {
                //     Metadata(x.id)
                // }
            }
        }
    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        msg match
            case GroupManagementMessage.ViewMembers =>
                model.copy(viewing = ViewingWhat.Players)
            case GroupManagementMessage.ViewRoles =>
                model.copy(viewing = ViewingWhat.Roles)
            case GroupManagementMessage.InviteMember =>
                ???
            case GroupManagementMessage.ClickRole(role) =>
                ???
        
enum RoleManagementMessage:
    case DeleteRole

class RoleManagementProgram(using gm: GroupManager) extends UIProgram:
    import io.circe.generic.auto._

    case class Flags(groupID: GroupID, roleID: RoleID, userID: UserID)
    case class Model(group: GroupState, groupID: GroupID, userID: UserID, role: RoleState)
    type Message = RoleManagementMessage

    override def init(flags: Flags): Model =
        val group = gm.getGroup(flags.groupID).toOption.get
        val role = group.roles.find(_.id == flags.roleID).get
        Model(group, flags.groupID, flags.userID, role)
    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        msg match
            case RoleManagementMessage.DeleteRole =>
                // TODO: error reporting
                gm.deleteRole(model.userID, model.role.id, model.groupID).toOption.get
                val p = GroupManagementProgram()
                services.transferTo(p, p.Flags(model.groupID, model.userID))
                model
    override def view(model: Model): Elem =
        val role = model.role
        val group = model.group
        Root(s"Viewing Role ${role.name} in ${group.metadata.name}", 6) {
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                OutlinePane(0, 0, 1, 6) {
                    if role.id != nullUUID then
                        Button("lava_bucket", "§aDelete Role", RoleManagementMessage.DeleteRole)()
                }
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            OutlinePane(2, 0, 7, 6) {
                
            }
        }

enum GroupListMessage:
    case ClickGroup(groupID: GroupID)
    case CreateGroup

class GroupListProgram(using gm: GroupManager) extends UIProgram:
    import io.circe.generic.auto._

    case class Flags(userID: UserID)
    case class Model(userID: UserID, groups: List[GroupStates])
    type Message = GroupListMessage

    override def init(flags: Flags): Model =
        Model(flags.userID, gm.userGroups(flags.userID).toOption.get)

    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        msg match
            case GroupListMessage.ClickGroup(groupID) =>
                val p = GroupManagementProgram()
                services.transferTo(p, p.Flags(groupID, model.userID))
                model
            case GroupListMessage.CreateGroup =>
                val answer = services.prompt("What do you want to call the group?")
                answer.map { result =>
                    gm.createGroup(model.userID, result)
                    model.copy(groups = gm.userGroups(model.userID).toOption.get)
                }

    override def view(model: Model): Elem =
        val groups = model.groups
        Root("Groups", 6) {
            OutlinePane(0, 0, 1, 6) {
                Button("name_tag", "§aCreate Group", GroupListMessage.CreateGroup)()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            OutlinePane(2, 0, 7, 6) {
                groups.foreach { x =>
                    Button("leather_chestplate", s"§a${x.name}", GroupListMessage.ClickGroup(x.id))()
                }
            }
        }