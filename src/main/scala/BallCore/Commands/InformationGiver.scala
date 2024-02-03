package BallCore.Commands

import BallCore.Chat.ChatActor
import BallCore.TextComponents.*
import org.bukkit.plugin.Plugin
import org.bukkit.Bukkit
import java.util.concurrent.TimeUnit

class InformationGiver():
    private val informations = List(
        trans"commands.information.donate".args(
            ChatActor.linkIt("https://opencollective.com/civcubed"),
        ),
        trans"commands.information.vote".args(
            txt("/vote").color(Colors.teal),
        ),
        trans"commands.information.book".args(
            txt("/book").color(Colors.teal),
        ),
        trans"commands.information.rest",
        trans"commands.information.relay".args(
            txt("/relay").color(Colors.teal),
        ),
        trans"commands.information.plants".args(
            txt("/plants").color(Colors.teal),
        ),
        trans"commands.information.breaks",
        trans"commands.information.adaptation",
        trans"commands.information.discord".args(
            ChatActor.linkIt("https://discord.civcubed.net"),
        ),
        trans"commands.information.workstations".args(
            txt("/workstations").color(Colors.teal),
        ),
    ).map(trans"commands.information.skeleton".args(_))
    private var informationCounter = 0

    private def sendInformation(): Unit =
        Bukkit.getServer().sendServerMessage(informations(informationCounter))
        informationCounter = (informationCounter + 1) % informations.size

    def register()(using p: Plugin): Unit =
        val _ = p
            .getServer()
            .getAsyncScheduler()
            .runAtFixedRate(
                p,
                _ => this.sendInformation(),
                1,
                13,
                TimeUnit.MINUTES,
            )
