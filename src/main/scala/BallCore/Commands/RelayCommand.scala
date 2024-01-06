package BallCore.Commands

import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import org.bukkit.entity.Player
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import scala.jdk.FutureConverters._
import dev.jorel.commandapi.arguments.TextArgument
import org.bukkit.command.CommandSender
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import com.mojang.brigadier.suggestion.Suggestions
import dev.jorel.commandapi.SuggestionInfo
import BallCore.Groups.GroupStates
import cats.effect.IO
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import BallCore.WebHooks.WebHookManager

class RelayCommand(using
    sql: SQLManager,
    webhooks: WebHookManager,
    gm: GroupManager,
):
    val addHook =
        LiteralArgument("add-hook")
            .`then`(
                GreedyStringArgument("url")
                    .executesPlayer(withGroupArgument("group") { (sender, args, group) =>
                        sql.useFireAndForget(for {
                            result <- sql.withS(
                                sql.withTX(
                                    webhooks.addWebHook(
                                        sender.getUniqueId,
                                        group.id,
                                        args.getUnchecked[String]("url"),
                                    )
                                )
                            )
                            _ <- result match
                                case Left(err) =>
                                    IO {
                                        sender.sendServerMessage(
                                            txt"Webhook not added because ${err}"
                                        )
                                    }
                                case Right(_) =>
                                    IO {
                                        sender.sendServerMessage(
                                            txt"Webhook added!"
                                        )
                                    }
                        } yield ())
                    })
            )

    private def suggestURLs(
        context: SuggestionInfo[CommandSender],
        builder: SuggestionsBuilder,
    ): CompletableFuture[Suggestions] =
        val player = context.sender().asInstanceOf[Player]
        val group = context.previousArgs().getUnchecked[String]("group")

        sql.useFuture {
            sql.withS(sql.withTX(for {
                grp <- gm
                    .userGroups(player.getUniqueId)
                    .map(_.find(_.name == group))
                    .value
                suggestions <- grp match
                    case Right(Some(state)) =>
                        for {
                            hooks <- webhooks
                                .getWebHooksFor(player.getUniqueId, state.id)
                                .map(_.getOrElse(List()))
                        } yield
                            hooks.foreach { uri =>
                                builder.suggest(uri.toString)
                            }
                            builder.build()
                    case _ =>
                        IO { builder.build() }
            } yield suggestions))
        }.asJava
            .toCompletableFuture()

    val deleteHook =
        LiteralArgument("delete-hook")
            .`then`(
                GreedyStringArgument("url")
                    .replaceSuggestions(suggestURLs)
                    .executesPlayer(withGroupArgument("group") { (sender, args, group) =>
                        sql.useFireAndForget(for {
                            result <- sql.withS(
                                sql.withTX(
                                    webhooks.removeWebHook(
                                        sender.getUniqueId,
                                        group.id,
                                        args.getUnchecked[String]("url"),
                                    )
                                )
                            )
                            _ <- result match
                                case Left(err) =>
                                    IO {
                                        sender.sendServerMessage(
                                            txt"Webhook not removed because ${err}"
                                        )
                                    }
                                case Right(_) =>
                                    IO {
                                        sender.sendServerMessage(
                                            txt"Webhook removed!"
                                        )
                                    }
                        } yield ())
                    })
            )

    val root =
        CommandTree("relay").`then`(
            TextArgument("group")
                .replaceSuggestions(suggestGroups(true))
                .`then`(addHook)
                .`then`(deleteHook)
        )

