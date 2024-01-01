package BallCore.Commands

import dev.jorel.commandapi.CommandTree
import BallCore.TextComponents._
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextColor

class VoteCommand:
    private val urlColor: TextColor = TextColor.fromHexString("#2aa1bf")
    private def url(text: String): Component =
        txt(text)
            .clickEvent(ClickEvent.openUrl(text))
            .decoration(TextDecoration.UNDERLINED, true)
            .color(urlColor)
    val tree = CommandTree("vote")
        .executesPlayer({ (sender, args) =>
            sender.sendServerMessage(
                txt"Vote daily on the following servers to receive bonus essence!"
            )
            sender.sendServerMessage(
                url("https://minecraft-server-list.com/server/499283/vote/")
            )
            sender.sendServerMessage(
                url("https://minecraftservers.org/vote/658204")
            )
            sender.sendServerMessage(
                url("https://minecraft-mp.com/server/327471/vote/")
            )
        }: PlayerCommandExecutor)
