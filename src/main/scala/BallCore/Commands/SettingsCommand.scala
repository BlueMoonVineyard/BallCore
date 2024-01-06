package BallCore.Commands

import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.command.CommandSender
import cats.effect.IO
import BallCore.Storage.KeyVal
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import java.util.TimeZone
import java.time.format.TextStyle
import java.util.Locale
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SettingsCommand(using sql: SQLManager, kv: KeyVal):
    private val root = CommandTree("settings")

    import BallCore.PrimeTime.TimeZoneCodec.{encoder, decoder}
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy")

    private val timeZone = LiteralArgument("timezone")
        .executesPlayer({ (sender, args) =>
            sql.useFireAndForget(sql.withS(for {
                tz <- kv.get[TimeZone](sender.getUniqueId, "time-zone")
                _ <- tz match
                    case Some(tz) =>
                        IO {
                            val zid = tz.toZoneId()
                            val id = tz.getID()
                            val name =
                                zid.getDisplayName(TextStyle.FULL, Locale.US)
                            val now = formatter.format(ZonedDateTime.now(zid))
                            sender.sendServerMessage(
                                txt"Your current time zone is ${id} (${name}). The current time is ${now}."
                            )
                        }
                    case None =>
                        IO {
                            sender.sendServerMessage(
                                txt"You have no time zone set currently."
                            )
                        }
            } yield ()))
        }: PlayerCommandExecutor)
        .`then`(
            GreedyStringArgument("new-timezone")
                .replaceSuggestions({ (context, builder) =>
                    TimeZone
                        .getAvailableIDs()
                        .filter(x => x.startsWith(context.currentArg()))
                        .foreach(builder.suggest)
                    builder.buildFuture()
                })
                .executesPlayer({ (sender, args) =>
                    val tz = TimeZone
                        .getTimeZone(args.getUnchecked[String]("new-timezone"))
                    val zid = tz.toZoneId()
                    val id = tz.getID()
                    val name = zid.getDisplayName(TextStyle.FULL, Locale.US)
                    val now = formatter.format(ZonedDateTime.now(zid))
                    sql.useFireAndForget(sql.withS(for {
                        _ <- kv.set(sender.getUniqueId, "time-zone", tz)
                        _ <- IO {
                            sender.sendServerMessage(
                                txt"Your time zone has been set to ${id} (${name}). The current time is ${now}."
                            )
                        }
                    } yield ()))
                }: PlayerCommandExecutor)
        )

    val node = root.`then`(timeZone)
