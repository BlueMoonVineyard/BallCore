package BallCore.Commands

import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import org.bukkit.entity.Player
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import BallCore.OneTimeTeleport.OneTimeTeleporter
import dev.jorel.commandapi.arguments.PlayerArgument
import BallCore.OneTimeTeleport.OTTError
import net.kyori.adventure.text.Component
import cats.effect.IO

class OTTCommand(using sql: SQLManager, ott: OneTimeTeleporter):
    private def errorText(err: OTTError): Component =
        err match
            case OTTError.alreadyUsedTeleport =>
                trans"errors.ott.already-used-teleport"
            case OTTError.isNotTeleportingToYou =>
                trans"errors.ott.is-not-teleporting-to-you"
            case OTTError.teleportFailed =>
                trans"errors.ott.teleport-failed"

    val node =
        CommandTree("ott")
            .`then`(
                LiteralArgument("request")
                    .`then`(
                        PlayerArgument("target")
                            .executesPlayer({ (sender, args) =>
                                val target = args.getUnchecked[Player]("target")
                                sql.useFireAndForget(for {
                                    res <- sql.withS(
                                        ott.requestTeleportTo(sender, target)
                                    )
                                    _ <- IO {
                                        res match
                                            case Left(err) =>
                                                sender.sendServerMessage(
                                                    errorText(err)
                                                )
                                            case Right(_) =>
                                                val command =
                                                    trans"sample-commands.ott-accept"
                                                        .args(
                                                            sender.getName.toComponent
                                                        )
                                                        .color(Colors.teal)
                                                target.sendServerMessage(
                                                    trans"notifications.received-ott-request"
                                                        .args(
                                                            sender
                                                                .displayName(),
                                                            command,
                                                        )
                                                )
                                    }
                                } yield ())
                            }: PlayerCommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("accept")
                    .`then`(
                        PlayerArgument("target")
                            .executesPlayer({ (sender, args) =>
                                val target = args.getUnchecked[Player]("target")
                                sql.useFireAndForget(for {
                                    res <- sql.withS(
                                        ott.acceptTeleportOf(sender, target)
                                    )
                                    _ <- IO {
                                        res match
                                            case Left(err) =>
                                                sender.sendServerMessage(
                                                    errorText(err)
                                                )
                                            case Right(_) =>
                                                sender.sendServerMessage(
                                                    txt"Teleport request accepted."
                                                )
                                    }
                                } yield ())
                            }: PlayerCommandExecutor)
                    )
            )
