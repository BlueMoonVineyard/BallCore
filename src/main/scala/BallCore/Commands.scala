// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore

import BallCore.Acclimation.{AcclimationActor, AcclimationMessage, Information}
import BallCore.Beacons.CivBeaconManager
import BallCore.Chat.{ChatActor, ChatMessage}
import BallCore.CustomItems.ItemRegistry
import BallCore.Groups.GroupManager
import BallCore.Plants.{PlantBatchManager, PlantListProgram, PlantMsg}
import BallCore.PolygonEditor.PolygonEditor
import BallCore.PolyhedraEditor.PolyhedraEditor
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import BallCore.UI.UIProgramRunner
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import dev.jorel.commandapi.arguments.NamespacedKeyArgument
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.arguments.GreedyStringArgument
import scala.jdk.FutureConverters._
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import BallCore.Folia.FireAndForget
import dev.jorel.commandapi.arguments.TextArgument
import org.bukkit.command.CommandSender
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import com.mojang.brigadier.suggestion.Suggestions
import dev.jorel.commandapi.SuggestionInfo
import dev.jorel.commandapi.executors.CommandArguments
import BallCore.Groups.GroupStates
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import org.bukkit.OfflinePlayer
import BallCore.Groups.nullUUID
import BallCore.Groups.Permissions

class CheatCommand(using
    registry: ItemRegistry,
    pbm: PlantBatchManager,
    aa: AcclimationActor,
    storage: BallCore.Acclimation.Storage,
    sql: SQLManager,
):
    val node =
        CommandTree("cheat")
            .withRequirement(_.hasPermission("ballcore.cheat"))
            .`then`(
                LiteralArgument("spawn")
                    .`then`(
                        NamespacedKeyArgument("item")
                            .executesPlayer({ (sender, args) =>
                                registry.lookup(
                                    args.getUnchecked[NamespacedKey]("item")
                                ) match
                                    case None =>
                                        sender.sendServerMessage(
                                            txt"Unknown item"
                                        )
                                    case Some(item) =>
                                        val is = item.template.clone()
                                        sender.getInventory.addItem(is)
                                        sender.sendServerMessage(
                                            txt"Gave 1 item"
                                        )
                            }: PlayerCommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("tick-plants")
                    .executesPlayer({ (sender, args) =>
                        pbm.send(PlantMsg.tickPlants)
                        sender.sendServerMessage(
                            txt"An hour of ingame time has passed"
                        )
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("tick-acclimation")
                    .executesPlayer({ (sender, args) =>
                        aa.send(AcclimationMessage.tick)
                        sender.sendServerMessage(
                            txt"Six hours of ingame time have passed"
                        )
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("my-acclimation")
                    .executesPlayer({ (sender, args) =>
                        val plr = sender.asInstanceOf[Player]
                        val uuid = sender.asInstanceOf[Player].getUniqueId
                        val (aElevation, aLatitude, aLongitude, aTemperature) =
                            sql.useBlocking(for {
                                elevation <- storage.getElevation(uuid)
                                latitude <- storage.getLatitude(uuid)
                                longitude <- storage.getLongitude(uuid)
                                temperature <- storage.getTemperature(uuid)
                            } yield (elevation, latitude, longitude, temperature))
                        import Information.*
                        sender.sendServerMessage(
                            txt"Your current elevation: ${elevation(plr.getLocation().getY.toInt).toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted elevation: ${aElevation.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )
                        val (lat, long) =
                            latLong(
                                plr.getLocation().getX,
                                plr.getLocation().getZ,
                            )
                        sender.sendServerMessage(
                            txt"Your current latitude: ${lat.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted latitude: ${aLatitude.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )
                        sender.sendServerMessage(
                            txt"Your current longitude: ${lat.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted longitude: ${aLatitude.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )
                        val temp = temperature(
                            plr.getLocation().getX.toInt,
                            plr.getLocation().getY.toInt,
                            plr.getLocation().getZ.toInt,
                        )
                        sender.sendServerMessage(
                            txt"Your current temperature: ${temp.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted temperature: ${aTemperature.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )

                        val dlat = Information.similarityNeg(lat, aLatitude)
                        val dlong = Information.similarityNeg(long, aLongitude)

                        // multiplier of the bonus on top of baseline rate
                        val bonusRateMultiplier = (dlat + dlong) / 2.0
                        sender.sendServerMessage(
                            txt"Your bonus rate multiplier for mining: ${bonusRateMultiplier.toComponent
                                    .style(NamedTextColor.GOLD)}"
                        )
                    }: PlayerCommandExecutor)
            )

private def suggestGroups(using sql: SQLManager, gm: GroupManager)(
    escaped: Boolean
)(
    context: SuggestionInfo[CommandSender],
    builder: SuggestionsBuilder,
): CompletableFuture[Suggestions] =
    val player = context.sender().asInstanceOf[Player]

    sql.useFuture {
        gm.userGroups(player.getUniqueId)
            .value
            .map(_.toOption.get)
            .map { groups =>
                groups
                    .filter(
                        _.name
                            .toLowerCase()
                            .contains(
                                context.currentArg().toLowerCase()
                            )
                    )
                    .foreach(it =>
                        builder.suggest(
                            if !escaped then it.name
                            else "\"" + it.name.replaceAll("\"", "\\\"") + "\""
                        )
                    )
                builder.build()
            }
    }.asJava
        .toCompletableFuture()

private def withGroupArgument(using sql: SQLManager, gm: GroupManager)(
    name: String
)(
    fn: (Player, CommandArguments, GroupStates) => Unit
): PlayerCommandExecutor = { (sender, args) =>
    val group = args.getUnchecked[String](name)

    sql
        .useBlocking {
            gm.userGroups(sender.getUniqueId).value
        }
        .map(_.find(_.name == group)) match
        case Left(err) =>
            sender.sendServerMessage(
                err.explain().toComponent
            )
        case Right(Some(group)) =>
            fn(sender, args, group)
        case Right(None) =>
            sender.sendServerMessage(
                txt"I couldn't find a group matching '$group'"
            )
}

class GroupsCommand(using
    prompts: UI.Prompts,
    plugin: Plugin,
    gm: GroupManager,
    cbm: CivBeaconManager,
    sql: SQLManager,
    e: PolyhedraEditor,
):
    val inviteNode =
        LiteralArgument("invite")
            .`then`(
                OfflinePlayerArgument("player")
                    .executesPlayer(withGroupArgument("group") { (sender, args, group) =>
                        val target =
                            args.getUnchecked[OfflinePlayer]("player")
                        if target.getName() == null then
                            sender.sendServerMessage(
                                txt"That player has never joined CivCubed"
                            )
                        sql.useBlocking(gm.getGroup(group.id).value) match
                            case Left(err) =>
                                sender.sendServerMessage(
                                    txt"Could not invite ${target
                                            .getName()} because ${err.explain()}"
                                )
                            case Right(fullGroup) =>
                                if fullGroup.check(
                                        Permissions.InviteUser,
                                        sender.getUniqueId,
                                        nullUUID,
                                    )
                                then
                                    if fullGroup.users.contains(
                                            target.getUniqueId
                                        )
                                    then
                                        sender.sendServerMessage(
                                            txt"${target.getName} is already in ${group.name}"
                                        )
                                    else
                                        sql.useBlocking(
                                            gm.invites.inviteToGroup(
                                                sender.getUniqueId,
                                                target.getUniqueId,
                                                group.id,
                                            )
                                        )
                                        sender.sendServerMessage(
                                            txt"Invited ${target.getName} to ${group.name}"
                                        )
                                else
                                    sender.sendServerMessage(
                                        txt"You do not have the permission to invite people to ${group.name}"
                                    )

                    })
            )

    val individualGroupNode =
        TextArgument("group")
            .replaceSuggestions(suggestGroups(true))
            .executesPlayer(withGroupArgument("group") {
                (sender, args, group) =>
                    given ExecutionContext = EntityExecutionContext(sender)
                    FireAndForget {
                        val p = Groups.GroupManagementProgram()
                        val runner = UIProgramRunner(
                            p,
                            p.Flags(group.id, sender.getUniqueId),
                            sender,
                        )
                        runner.render()
                    }
            })
            .`then`(inviteNode)

    val node =
        CommandTree("groups")
            .executesPlayer({ (sender, args) =>
                given ExecutionContext = EntityExecutionContext(sender)
                FireAndForget {
                    val p = Groups.GroupListProgram()
                    val runner =
                        UIProgramRunner(p, p.Flags(sender.getUniqueId), sender)
                    runner.render()
                }
            }: PlayerCommandExecutor)
            .`then`(
                individualGroupNode
            )

    val invitesNode =
        CommandTree("invites")
            .executesPlayer({ (sender, args) =>
                given ExecutionContext = EntityExecutionContext(sender)
                FireAndForget {
                    val p = Groups.InvitesListProgram()
                    val runner =
                        UIProgramRunner(p, p.Flags(sender.getUniqueId), sender)
                    runner.render()
                }
            }: PlayerCommandExecutor)

class PlantsCommand(using prompts: UI.Prompts, plugin: Plugin):
    val node =
        CommandTree("plants")
            .executesPlayer({ (sender, args) =>
                given ExecutionContext = EntityExecutionContext(sender)
                FireAndForget {
                    val p = PlantListProgram()
                    val runner = UIProgramRunner(p, p.Flags(), sender)
                    runner.render()
                }
            }: PlayerCommandExecutor)

class DoneCommand(using
    editor: PolygonEditor,
    polyhedraEditor: PolyhedraEditor,
):
    val node =
        CommandTree("done")
            .executesPlayer({ (sender, args) =>
                val plr = sender.asInstanceOf[Player]
                editor.done(plr)
                polyhedraEditor.done(plr)
            }: PlayerCommandExecutor)

class DeclareCommand(using
    editor: PolygonEditor,
):
    val node =
        CommandTree("declare")
            .executesPlayer({ (sender, args) =>
                editor.declare(sender)
            }: PlayerCommandExecutor)

class ChatCommands(using ca: ChatActor, gm: GroupManager, sql: SQLManager):
    val group =
        CommandTree("group")
            .`then`(
                GreedyStringArgument("group-to-chat-in")
                    .replaceSuggestions(suggestGroups(false))
                    .executesPlayer(withGroupArgument("group-to-chat-in") {
                        (sender, args, group) =>
                            ca.send(
                                ChatMessage
                                    .chattingInGroup(sender, group.id)
                            )
                    })
            )

    val global =
        CommandTree("global")
            .executesPlayer({ (sender, args) =>
                ca.send(ChatMessage.chattingInGlobal(sender))
            }: PlayerCommandExecutor)

    val local =
        CommandTree("local")
            .executesPlayer({ (sender, args) =>
                ca.send(ChatMessage.chattingInLocal(sender))
            }: PlayerCommandExecutor)
