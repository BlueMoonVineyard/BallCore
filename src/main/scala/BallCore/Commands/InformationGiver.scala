package BallCore.Commands

import BallCore.Chat.ChatActor
import BallCore.TextComponents.*
import org.bukkit.plugin.Plugin
import org.bukkit.Bukkit
import java.util.concurrent.TimeUnit

class InformationGiver():
    private val informations = List(
        txt"[CivCubed] Consider helping us keep the lights on by donating to https://opencollective.com/civcubed!",
        txt"[CivCubed] ${txt("/vote").color(Colors.teal)} to get some more essence every day!",
        txt"[CivCubed] Browse the selection of ${txt("/book").color(Colors.teal)} and learn more about the server!",
        txt"[CivCubed] Rest accumulates when you log off and come back the next day!",
        txt"[CivCubed] Set up a relay of important events to a Discord webhook with ${txt("/relay")
                .color(Colors.teal)}!",
        txt"[CivCubed] See what plants grow in your area with ${txt("/plants")
                .color(Colors.teal)}!",
        txt"[CivCubed] Remember to take breaks and drink plenty of water!",
        txt"[CivCubed] The more time you spend somewhere, the more ores you'll get when you mine!",
        txt"[CivCubed] Join the Discord at https://discord.civcubed.net!",
        txt"[CivCubed] Use ${txt("/workstations").color(Colors.teal)} to view the list of workstations!",
    ).map(_.replaceText(ChatActor.urlReplacer))
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
