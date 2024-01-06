package BallCore.Commands

import BallCore.TextComponents.*
import org.bukkit.plugin.Plugin
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.command.CommandSender
import dev.jorel.commandapi.arguments.IntegerArgument
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import java.util.concurrent.TimeUnit

class RestartTimer():
    private var duration: Int = -1
    private var time: Int = -1
    private var ticking: Boolean = false
    private var bossBar: BossBar = null

    private def tick(): Unit =
        if ticking && bossBar != null then
            time = time - 1
            if time <= 0 then
                bossBar.progress(1.0f)
                bossBar.color(BossBar.Color.RED)
                val _ = bossBar.name(txt"Restarting...")
            else
                bossBar.progress(time.toFloat / duration.toFloat)
                val _ = bossBar.name(
                    txt"Restart Timer: You will be sent to the hub in ${time} seconds"
                )

    private val node =
        CommandTree("timer")
            .withRequirement(_.hasPermission("ballcore.timer"))
            .`then`(
                LiteralArgument("start")
                    .`then`(
                        IntegerArgument("seconds", 1)
                            .executesPlayer({ (sender, args) =>
                                val seconds = args
                                    .getUnchecked[Integer]("seconds")
                                    .intValue()
                                time = seconds
                                duration = seconds

                                if bossBar != null then
                                    val _ =
                                        bossBar.removeViewer(Bukkit.getServer())

                                bossBar = BossBar.bossBar(
                                    txt"Restart Timer: You will be sent to the hub in ${time} seconds",
                                    1.0f,
                                    BossBar.Color.GREEN,
                                    BossBar.Overlay.PROGRESS,
                                )
                                val _ = bossBar.addViewer(Bukkit.getServer())

                                ticking = true
                            }: PlayerCommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("stop")
                    .executesPlayer({ (sender, args) =>
                        if bossBar != null then
                            val _ = bossBar.removeViewer(Bukkit.getServer())
                        ticking = false
                    }: PlayerCommandExecutor)
            )

    def register()(using p: Plugin): Unit =
        node.register()
        val _ = p
            .getServer()
            .getAsyncScheduler()
            .runAtFixedRate(p, _ => this.tick(), 1, 1, TimeUnit.SECONDS)
