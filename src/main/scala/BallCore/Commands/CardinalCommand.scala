package BallCore.Commands

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.arguments.LiteralArgument
import org.bukkit.Location
import dev.jorel.commandapi.arguments.Argument

class CardinalCommand:
    private def direction(
        name: String,
        it: Location => Unit,
    ): Argument[String] =
        LiteralArgument(name)
            .executesPlayer({ (player, args) =>
                val here = player.getLocation()
                it(here)
                val _ = player.teleportAsync(here)
            }: PlayerCommandExecutor)

    val tree = CommandTree("cardinal")
        .executesPlayer({ (player, args) =>
            val here = player.getLocation
            val newYaw = Math.rint(here.getYaw / 45f).toFloat * 45f
            here.setYaw(newYaw)
            val _ = player.teleportAsync(here)
        }: PlayerCommandExecutor)
        .`then`(direction("up", _.setPitch(-90f)))
        .`then`(direction("down", _.setPitch(90f)))
        .`then`(direction("north", _.setYaw(180f)))
        .`then`(direction("south", _.setYaw(0f)))
        .`then`(direction("east", _.setYaw(-90f)))
        .`then`(direction("west", _.setYaw(90)))
