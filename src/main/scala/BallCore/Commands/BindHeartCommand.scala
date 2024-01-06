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
                                            txt"You don't have a Civilization Beacon to bind ${group.name} to!"
                                        )
                                    case Some(Left(_)) =>
                                        sender.sendServerMessage(
                                            txt"Failed to bind ${group.name} to your Civilization Beacon!"
                                        )
                                    case Some(Right(_)) =>
                                        BindCivHeart.grant(sender, "bind")
                                        sender.sendServerMessage(
                                            txt"Bound ${group.name} to your Civilization Beacon!"
                                        )
                                        sender.sendServerMessage(
                                            txt"You can now right-click it to set up an area of protection!"
                                        )
                            }
                        } yield ())
                    })
            )
