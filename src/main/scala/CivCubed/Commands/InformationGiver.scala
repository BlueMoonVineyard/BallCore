package CivCubed.Commands

import CivCubed.Chat.ChatActor
import CivCubed.TextComponents.*
import org.bukkit.plugin.Plugin
import org.bukkit.Bukkit
import java.util.concurrent.TimeUnit

class InformationGiver():
    private val informations = List(
        trans"commands.information.donate".arguments(
            ChatActor.linkIt("https://opencollective.com/civcubed"),
        ),
        trans"commands.information.vote".arguments(
            txt("/vote").color(Colors.teal),
        ),
        trans"commands.information.book".arguments(
            txt("/book").color(Colors.teal),
        ),
        trans"commands.information.rest",
        trans"commands.information.relay".arguments(
            txt("/relay").color(Colors.teal),
        ),
        trans"commands.information.plants".arguments(
            txt("/plants").color(Colors.teal),
        ),
        trans"commands.information.breaks",
        trans"commands.information.adaptation",
        trans"commands.information.discord".arguments(
            ChatActor.linkIt("https://discord.civcubed.net"),
        ),
        trans"commands.information.workstations".arguments(
            txt("/workstations").color(Colors.teal),
        ),
    ).map(trans"commands.information.skeleton".arguments(_))
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
