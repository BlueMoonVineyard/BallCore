// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.UI.UI
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

class GroupManagementProgram(using gm: GroupManager) extends UIProgram:
    import io.circe.generic.auto._

    override def init(flags: Flags): Model = ???
    override def view(model: Model): Elem = ???
    override def update(msg: Message, model: Model, services: UIServices): Model = ???

class RoleManagementProgram(using gm: GroupManager) extends UIProgram:
    import io.circe.generic.auto._

    case class Flags(groupID: GroupID, roleID: RoleID, userID: UserID)
    case class Model(group: GroupState, role: RoleState)
    enum Message:
        case DeleteRole

    override def init(flags: Flags): Model =
        val group = gm.getGroup(flags.groupID).toOption.get
        val role = group.roles.find(_.id == flags.roleID).get
        Model(group, role)
    override def update(msg: Message, model: Model, services: UIServices): Model =
        msg match
            case Message.DeleteRole => 
                services.transferTo(???, ???)
                model
    override def view(model: Model): Elem =
        val role = model.role
        val group = model.group
        Root(s"Viewing Role ${role.name} in ${group.metadata.name}", 6) {
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                OutlinePane(0, 0, 1, 6) {
                    if role.id != nullUUID then
                        Button("lava_bucket", "§aDelete Role", Message.DeleteRole)()
                }
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            OutlinePane(2, 0, 7, 6) {
                
            }
        }

class RoleManagementUI(groupID: GroupID, roleID: RoleID, target: HumanEntity)(using prompts: Prompts, gm: GroupManager) extends UI:
    showingTo = target

    var group = loadGroup().toOption.get
    var role = group.roles.find(_.id == roleID).get
    def loadGroup() =
        gm.getGroup(groupID)

    override def view(): Elem =
        Root(s"Viewing Role ${role.name} in ${group.metadata.name}", 6) {
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                OutlinePane(0, 0, 1, 6) {
                    // if roleID != nullUUID then
                    //     Item("lava_bucket", displayName = Some("§aDelete Role"), onClick = callback(deleteRole))()
                }
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            OutlinePane(2, 0, 7, 6) {
                
            }
        }
    def deleteRole(event: InventoryClickEvent): Unit =
        event.setCancelled(true)
        gm.deleteRole(target.getUniqueId(), roleID, groupID)
            .map { x =>
                val newUI = GroupManagementUI(groupID, target)
                newUI.queueUpdate()
            }

class GroupManagementUI(groupID: GroupID, target: HumanEntity)(using prompts: Prompts, gm: GroupManager) extends UI:
    import io.circe.generic.auto._

    enum ViewingWhat:
        case Players
        case Roles

    var group = loadGroup().toOption.get
    var viewing = ViewingWhat.Players
    def loadGroup() =
        gm.getGroup(groupID)

    showingTo = target
    override def view(): Elem =
        Root(s"Viewing ${group.metadata.name}", 6) {
            OutlinePane(0, 0, 1, 6) {
                // Item("player_head", displayName = Some("§aMembers"), onClick = callback(members))()
                // if group.check(Permissions.ManageRoles, target.getUniqueId()) then
                //     Item("writable_book", displayName = Some("§aManage Roles"), onClick = callback(manageRoles))()
                // if group.check(Permissions.InviteUser, target.getUniqueId()) then
                //     Item("compass", displayName = Some("§aInvite A Member"))()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            viewing match
                case ViewingWhat.Players => Players
                case ViewingWhat.Roles => Roles
        }
    def members(event: InventoryClickEvent): Unit =
        event.setCancelled(true)
        if viewing == ViewingWhat.Players then
            return
        viewing = ViewingWhat.Players
        queueUpdate()
    def manageRoles(event: InventoryClickEvent): Unit =
        event.setCancelled(true)
        if viewing == ViewingWhat.Roles then
            return
        viewing = ViewingWhat.Roles
        queueUpdate()
    def clickRole(event: InventoryClickEvent, role: RoleID): Unit =
        event.setCancelled(true)
        val newUI = RoleManagementUI(groupID, role, target)
        newUI.queueUpdate()
    def Players(using Accumulator): Unit =
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
    def Roles(using Accumulator): Unit =
        OutlinePane(2, 0, 7, 6) {
            group.roles.foreach { x =>
                // Item("leather_chestplate", displayName = Some(s"§r§f${x.name}"), onClick = callback(clickRole)) {
                //     Metadata(x.id)
                // }
            }
        }

class GroupListUI(target: HumanEntity)(using prompts: Prompts, gm: GroupManager) extends UI:
    var groups = loadGroups()
    def loadGroups() =
        gm.userGroups(target.getUniqueId()).toOption.get

    showingTo = target
    override def view(): Elem =
        Root("Groups", 6) {
            OutlinePane(0, 0, 1, 6) {
                // Item("name_tag", displayName = Some("§aCreate Group"), onClick = callback(createGroup))()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            OutlinePane(2, 0, 7, 6) {
                groups.foreach { x =>
                    // Item("leather_chestplate", displayName = Some(s"§a${x.name}"), onClick = callback(clickGroup)) {
                    //     Metadata(x)
                    // }
                }
            }
        }
    def createGroup(event: InventoryClickEvent): Unit =
        event.setCancelled(true)
        prompts.prompt(event.getWhoClicked().asInstanceOf[Player], "What do you want to call the group?")
            .map { result =>
                gm.createGroup(event.getWhoClicked().asInstanceOf[Player].getUniqueId(), result)
                groups = loadGroups()
                queueUpdate()
            }
    def clickGroup(event: InventoryClickEvent, s: GroupStates): Unit =
        event.setCancelled(true)

        val passingTo = GroupManagementUI(s.id, target)
        passingTo.queueUpdate()
