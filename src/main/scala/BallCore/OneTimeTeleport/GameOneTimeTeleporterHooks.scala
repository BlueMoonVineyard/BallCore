package BallCore.OneTimeTeleport

import org.bukkit.entity.Player
import cats.effect.IO
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause

class GameOneTimeTeleporterHooks extends OneTimeTeleporterHooks:
    override def teleport(source: Player, destination: Player): IO[Boolean] =
        IO.fromCompletableFuture(
            IO {
                source.teleportAsync(
                    destination.getLocation(),
                    TeleportCause.COMMAND,
                )
            }
        ).map(_.booleanValue())
