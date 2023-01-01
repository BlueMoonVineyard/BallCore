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
                Item("player_head", displayName = Some("§aMembers"), onClick = callback(members))()
                if group.check(Permissions.ManageRoles, target.getUniqueId()) then
                    Item("writable_book", displayName = Some("§aManage Roles"), onClick = callback(manageRoles))()
                if group.check(Permissions.InviteUser, target.getUniqueId()) then
                    Item("compass", displayName = Some("§aInvite A Member"))()
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
                Item("leather_chestplate", displayName = Some(s"§r§f${x.name}"))
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
                Item("name_tag", displayName = Some("§aCreate Group"), onClick = callback(createGroup))()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
            OutlinePane(2, 0, 7, 6) {
                groups.foreach { x =>
                    Item("leather_chestplate", displayName = Some(s"§a${x.name}"), onClick = callback(clickGroup)) {
                        Metadata(x)
                    }
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
