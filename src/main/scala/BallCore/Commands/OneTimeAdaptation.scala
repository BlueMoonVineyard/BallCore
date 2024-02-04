package BallCore.Commands

import BallCore.Acclimation.Information
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import cats.effect.IO
import BallCore.Storage.KeyVal
import io.circe.Encoder
import io.circe.Decoder

class OneTimeAdaptation(using
    sql: SQLManager,
    kv: KeyVal,
    as: BallCore.Acclimation.Storage,
):
    val node =
        CommandTree("one-time-adaptation")
            .executesPlayer({ (sender, args) =>
                sender.sendServerMessage(
                    trans"commands.one-time-adaptation.can-only-be-used-once"
                )
                sender.sendServerMessage(
                    trans"commands.one-time-adaptation.explanation"
                )
                sender.sendServerMessage(
                    trans"commands.one-time-adaptation.prompt-to-continue".args(
                        txt("/one-time-adaptation confirm").color(Colors.teal)
                    )
                )
            }: PlayerCommandExecutor)
            .`then`(
                LiteralArgument("confirm")
                    .executesPlayer({ (sender, args) =>
                        val x = sender.getX()
                        val y = sender.getY().toInt
                        val z = sender.getZ()
                        val temp = Information.temperature(x.toInt, y, z.toInt)
                        sql.useFireAndForget(sql.withS(sql.withTX(for {
                            hasUsed <- kv
                                .get[Boolean](sender.getUniqueId, "used-ota")
                            _ <- hasUsed match
                                case Some(x) if x =>
                                    IO {
                                        sender.sendServerMessage(
                                            trans"commands.one-time-adaptation.already-used"
                                        )
                                    }
                                case _ =>
                                    for {
                                        _ <- as.setElevation(
                                            sender.getUniqueId,
                                            Information.elevation(y),
                                        )
                                        latLong = Information.latLong(x, z)
                                        _ <- as.setLatitude(
                                            sender.getUniqueId,
                                            latLong._1,
                                        )
                                        _ <- as.setLongitude(
                                            sender.getUniqueId,
                                            latLong._2,
                                        )
                                        _ <- as.setTemperature(
                                            sender.getUniqueId,
                                            temp,
                                        )
                                        _ <- kv.set(
                                            sender.getUniqueId,
                                            "used-ota",
                                            true,
                                        )
                                        _ <- IO {
                                            sender.sendServerMessage(
                                                trans"commands.one-time-adaptation.successfully-used"
                                            )
                                        }
                                    } yield ()
                        } yield ())))
                    }: PlayerCommandExecutor)
            )
