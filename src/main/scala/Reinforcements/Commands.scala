// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.command.CommandExecutor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import scala.util.Try

class Commands() extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean =
        val plr = Try(sender.asInstanceOf[Player]).toOption
        args match
            case Array("reinforce", _*) =>
                plr.map { x =>
                    RuntimeStateManager.states(x.getUniqueId()) = Reinforcing()
                }
            case Array("unreinforce", _*) =>
                plr.map { x =>
                    RuntimeStateManager.states(x.getUniqueId()) = Unreinforcing()
                }
            case Array("neutral", _*) =>
                plr.map { x =>
                    RuntimeStateManager.states(x.getUniqueId()) = Neutral()
                }
        true
