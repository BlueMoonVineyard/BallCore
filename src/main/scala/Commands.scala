// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore

import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.Command
import org.bukkit.entity.Player
import BallCore.Groups.GroupManager
import BallCore.UI.UIProgramRunner
import org.bukkit.plugin.Plugin
import BallCore.CustomItems.ItemRegistry
import org.bukkit.NamespacedKey

class CheatCommand(using registry: ItemRegistry) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
        if !sender.permissionValue("ballcore.cheat").toBooleanOrElse(false) then
            sender.sendMessage("no cheating for u >:(")
            return false

        args match
            case Array(name, _*) =>
                registry.lookup(NamespacedKey.fromString(name)) match
                    case None =>
                        sender.sendMessage("bad item >:(")
                    case Some(item) =>
                        val is = item.template.clone()
                        sender.asInstanceOf[Player].getInventory().addItem(is)
                        sender.sendMessage("there u go :)")
            case _ =>
                sender.sendMessage("give me an item name >:(")
        true

class GroupsCommand(using prompts: UI.Prompts, plugin: Plugin, gm: GroupManager) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]) =
        val p = Groups.GroupListProgram()
        val plr = sender.asInstanceOf[Player]
        val runner = UIProgramRunner(p, p.Flags(plr.getUniqueId()), plr)
        runner.render()
        return true
