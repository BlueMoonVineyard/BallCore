// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.command.CommandExecutor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import scala.util.Try
import BallCore.Groups.GroupManager
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.items.VanillaItem

class Commands(using gm: GroupManager) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
        val plr = Try(sender.asInstanceOf[Player]).toOption
        args match
            case Array("reinforce", group, _*) =>
                plr.map { x =>
                    gm.userGroups(x.getUniqueId()).map(_.find(_.name.contains(group.toLowerCase()))) match
                        case Left(err) =>
                            x.sendMessage(err.explain())
                        case Right(Some(group)) =>
                            RuntimeStateManager.states(x.getUniqueId()) = Reinforcing(group.id)
                        case Right(None) =>
                            x.sendMessage(s"I couldn't find a group matching '${group}'")
                }
            case Array("reinforce", _*) =>
                plr.map { x =>
                    RuntimeStateManager.states(x.getUniqueId()) = Reinforcing(???)
                }
            case Array("unreinforce", _*) =>
                plr.map { x =>
                    RuntimeStateManager.states(x.getUniqueId()) = Unreinforcing()
                }
            case Array("neutral", _*) =>
                plr.map { x =>
                    RuntimeStateManager.states(x.getUniqueId()) = Neutral()
                }
            case Array("fortified", group, _*) =>
                plr.map { x =>
                    gm.userGroups(x.getUniqueId()).map(_.find(_.name.contains(group.toLowerCase()))) match
                        case Left(err) =>
                            x.sendMessage(err.explain())
                        case Right(Some(group)) =>
                            RuntimeStateManager.states(x.getUniqueId()) = ReinforceAsYouGo(group.id, x.getInventory().getItemInHand())
                        case Right(None) =>
                            x.sendMessage(s"I couldn't find a group matching '${group}'")
                }
            case Array("fortified", _*) =>
                plr.map { x =>
                    RuntimeStateManager.states(x.getUniqueId()) = Reinforcing(???)
                }
            case _ =>
                sender.sendMessage(
                    "Available commands:",
                    s"  /$label reinforce <group> - Begin reinforcing blocks on <group>",
                    s"  /$label unreinforce - Begin removing reforcements (that you have permissions to)",
                    s"  /$label neutral - Stop doing anything to reinforcements",
                    s"  /$label fortified <group> - Reinforce blocks as you place them, using the item you're holding now",
                )
        true
