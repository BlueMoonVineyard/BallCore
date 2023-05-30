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
import BallCore.Chat.ChatActor
import BallCore.Chat.ChatMessage
import BallCore.Plants.PlantBatchManager
import BallCore.Plants.PlantMsg

class CheatCommand(using registry: ItemRegistry, pbm: PlantBatchManager) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
        if !sender.permissionValue("ballcore.cheat").toBooleanOrElse(false) then
            sender.sendMessage("no cheating for u >:(")
            return false

        args match
            case Array("spawn", name, _*) =>
                registry.lookup(NamespacedKey.fromString(name)) match
                    case None =>
                        sender.sendMessage("bad item >:(")
                    case Some(item) =>
                        val is = item.template.clone()
                        sender.asInstanceOf[Player].getInventory().addItem(is)
                        sender.sendMessage("there u go :)")
            case Array("tick-plants", _*) =>
                pbm.send(PlantMsg.tickPlants)
                sender.sendMessage("an hour of ingame time has passed :)")
            case _ =>
                sender.sendMessage("bad cheating >:(")
        true

class GroupsCommand(using prompts: UI.Prompts, plugin: Plugin, gm: GroupManager) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]) =
        val p = Groups.GroupListProgram()
        val plr = sender.asInstanceOf[Player]
        val runner = UIProgramRunner(p, p.Flags(plr.getUniqueId()), plr)
        runner.render()
        return true

class ChatCommands(using ca: ChatActor, gm: GroupManager):
    object Group extends CommandExecutor:
        override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
            val p = sender.asInstanceOf[Player]
            val group = args(0)
            gm.userGroups(p.getUniqueId()).map(_.find(_.name.toLowerCase().contains(group.toLowerCase()))) match
                case Left(err) =>
                    p.sendMessage(err.explain())
                case Right(Some(group)) =>
                    ca.send(ChatMessage.chattingInGroup(p, group.id))
                case Right(None) =>
                    p.sendMessage(s"I couldn't find a group matching '${group}'")
            true
    object Global extends CommandExecutor:
        override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
            val p = sender.asInstanceOf[Player]
            ca.send(ChatMessage.chattingInGlobal(p))
            true
    object Local extends CommandExecutor:
        override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
            val p = sender.asInstanceOf[Player]
            ca.send(ChatMessage.chattingInLocal(p))
            true
