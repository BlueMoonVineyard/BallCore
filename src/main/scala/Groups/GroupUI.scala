// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.UI.UI
import BallCore.UI.Elements._
import scala.xml.Elem
import org.bukkit.entity.HumanEntity
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent

class GroupUI(target: HumanEntity) extends UI:
    showingTo = target
    override def view(): Elem =
        Root("Groups", 6) {
            OutlinePane(0, 0, 1, 6) {
                Item("name_tag", displayName = Some("§aCreate Group"), onClick = "createGroup")()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item("black_stained_glass_pane", displayName = Some(" "))()
            }
        }
    def createGroup(event: InventoryClickEvent): Unit =
        event.getWhoClicked().sendMessage(":)")
        event.setCancelled(true)