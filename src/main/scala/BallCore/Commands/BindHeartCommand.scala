package BallCore.Commands

import BallCore.Beacons.CivBeaconManager
import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.TextArgument
import org.bukkit.command.CommandSender
import cats.effect.IO
import cats.data.OptionT
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import BallCore.Advancements.BindCivHeart

class BindHeartCommand(using
    gm: GroupManager,
    sql: SQLManager,
    cbm: CivBeaconManager,
):
    val node =
        CommandTree("bind-heart")
            .`then`(
                TextArgument("group")
                    .replaceSuggestions(suggestGroups(true))
                    .executesPlayer(withGroupArgument("group") { (sender, args, group) =>
                        sql.useFireAndForget(for {
                            result <- sql.withS(
                                OptionT(
                                    cbm.getBeaconFor(sender.getUniqueId)
                                ).flatMap { beacon =>
                                    OptionT.liftF(
                                        cbm.setGroup(beacon, group.id)
                                    )
                                }.value
                            )
                            _ <- IO {
                                result match
                                    case None =>
                                        sender.sendServerMessage(
                                            trans"commands.bind-heart.no-civ-beacon".args(group.name.toComponent)
                                        )
                                    case Some(Left(_)) =>
                                        sender.sendServerMessage(
                                            trans"commands.bind-heart.failed".args(group.name.toComponent)
                                        )
                                    case Some(Right(_)) =>
                                        BindCivHeart.grant(sender, "bind")
                                        sender.sendServerMessage(
                                            trans"commands.bind-heart.success".args(group.name.toComponent)
                                        )
                                        sender.sendServerMessage(
                                            trans"commands.bind-heart.right-click"
                                        )
                            }
                        } yield ())
                    })
            )
