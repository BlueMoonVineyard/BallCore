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
import BallCore.PolygonEditor.PolygonEditor
import BallCore.Beacons.CivBeaconManager
import BallCore.Plants.PlantListProgram
import BallCore.Storage.SQLManager
import BallCore.PolyhedraEditor.PolyhedraEditor
import BallCore.Acclimation.AcclimationActor
import BallCore.Acclimation.AcclimationMessage
import BallCore.Acclimation.Information
import BallCore.TextComponents._
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

class CheatCommand(using registry: ItemRegistry, pbm: PlantBatchManager, aa: AcclimationActor, storage: BallCore.Acclimation.Storage, sql: SQLManager) extends CommandExecutor:
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
            case Array("tick-acclimation", _*) =>
                aa.send(AcclimationMessage.tick)
            case Array("my-acclimation", _*) =>
                val plr = sender.asInstanceOf[Player]
                val uuid = sender.asInstanceOf[Player].getUniqueId()
                val (aElevation, aLatitude, aLongitude, aTemperature) = sql.useBlocking(for {
                    elevation <- storage.getElevation(uuid)
                    latitude <- storage.getLatitude(uuid)
                    longitude <- storage.getLongitude(uuid)
                    temperature <- storage.getTemperature(uuid)
                } yield (elevation, latitude, longitude, temperature))
                import Information._
                sender.sendServerMessage(txt"Your current elevation: ${elevation(plr.getLocation().getY().toInt).toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted elevation: ${aElevation.toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)}")
                val (lat, long) = latLong(plr.getLocation().getX(), plr.getLocation().getZ())
                sender.sendServerMessage(txt"Your current latitude: ${lat.toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted latitude: ${aLatitude.toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)}")
                sender.sendServerMessage(txt"Your current longitude: ${lat.toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted longitude: ${aLatitude.toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)}")
                val temp = temperature(plr.getLocation().getX().toInt, plr.getLocation().getY().toInt, plr.getLocation().getZ().toInt)
                sender.sendServerMessage(txt"Your current temperature: ${temp.toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted temperature: ${aTemperature.toComponent.style(NamedTextColor.GOLD, TextDecoration.BOLD)}")

                val dlat = Information.similarityNeg(lat, aLatitude)
                val dlong = Information.similarityNeg(long, aLongitude)

                // multiplier of the bonus on top of baseline rate
                val bonusRateMultiplier = (dlat+dlong)/2.0
                sender.sendServerMessage(txt"Your bonus rate multiplier for mining: ${bonusRateMultiplier.toComponent.style(NamedTextColor.GOLD)}")
        true

class GroupsCommand(using prompts: UI.Prompts, plugin: Plugin, gm: GroupManager, cbm: CivBeaconManager, sql: SQLManager, e: PolyhedraEditor) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]) =
        val p = Groups.GroupListProgram()
        val plr = sender.asInstanceOf[Player]
        val runner = UIProgramRunner(p, p.Flags(plr.getUniqueId()), plr)
        runner.render()
        return true

class PlantsCommand(using prompts: UI.Prompts, plugin: Plugin) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]) =
        val p = PlantListProgram()
        val plr = sender.asInstanceOf[Player]
        val runner = UIProgramRunner(p, p.Flags(), plr)
        runner.render()
        return true

class DoneCommand(using editor: PolygonEditor, polyhedraEditor: PolyhedraEditor) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]) =
        val plr = sender.asInstanceOf[Player]
        editor.done(plr)
        polyhedraEditor.done(plr)
        true

class ChatCommands(using ca: ChatActor, gm: GroupManager, sql: SQLManager):
    object Group extends CommandExecutor:
        override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
            val p = sender.asInstanceOf[Player]
            val group = args(0)
            sql.useBlocking{ gm.userGroups(p.getUniqueId()).value }.map(_.find(_.name.toLowerCase().contains(group.toLowerCase()))) match
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
