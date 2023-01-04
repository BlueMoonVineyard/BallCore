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

class GroupsCommand(using prompts: UI.Prompts, gm: GroupManager) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]) =
        val p = Groups.GroupListProgram()
        val plr = sender.asInstanceOf[Player]
        val runner = UIProgramRunner(p, p.Flags(plr.getUniqueId()), plr)
        runner.render()
        return true
