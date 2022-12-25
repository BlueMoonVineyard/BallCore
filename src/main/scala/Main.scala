// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore

import BallCore.Hearts.Hearts
import BallCore.UI

import org.bukkit.plugin.java.JavaPlugin
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.Command
import org.bukkit.entity.Player
import scala.concurrent.ExecutionContext
import org.bukkit.Bukkit
import java.util.logging.Level

class TestUI(using prompts: UI.Prompts) extends CommandExecutor:
    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]) =
        val it = Groups.GroupUI(sender.asInstanceOf[Player])
        it.update()
        return true

final class Main extends JavaPlugin:
    given sql: Storage.SQLManager = new Storage.SQLManager
    given keyVal: Storage.SQLKeyVal = new Storage.SQLKeyVal
    given acclimation: Acclimation.Storage = new Acclimation.Storage
    given ballcore: Main = this
    given addon: SlimefunAddon = new BallCoreSFAddon
    given ec: ExecutionContext = new ExecutionContext:
        override def execute(runnable: Runnable): Unit =
            Bukkit.getScheduler().runTask(ballcore, runnable)
        override def reportFailure(cause: Throwable): Unit =
            getLogger().log(Level.WARNING, "Error in ExecutionContext:", cause)
    given prompts: UI.Prompts = new UI.Prompts(ec)
    
    override def onEnable() =
        Hearts.registerItems()
        getCommand("testui").setExecutor(TestUI())
    override def onDisable() =
        ()
