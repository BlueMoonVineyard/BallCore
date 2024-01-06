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
                    txt"This command can only be used one time."
                )
                sender.sendServerMessage(
                    txt"It will set your adaptation point to your current location, effectively maximising the bonuses you get for mining in this area."
                )
                sender.sendServerMessage(
                    txt"Run ${txt("/one-time-adaptation confirm").color(Colors.teal)} to continue."
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
                                            txt"You have already used up your one-time adaptation!"
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
                                                txt"You have successfully used your one-time adaptation!"
                                            )
                                        }
                                    } yield ()
                        } yield ())))
                    }: PlayerCommandExecutor)
            )
