package BallCore.Commands

import BallCore.Beacons.CivBeaconManager
import BallCore.Groups.GroupManager
import BallCore.PolygonEditor.PolygonEditor
import BallCore.PolyhedraEditor.PolyhedraEditor
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import BallCore.UI.UIProgramRunner
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
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
import cats.effect.IO
import BallCore.Storage.KeyVal
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import BallCore.PrimeTime.PrimeTimeManager
import BallCore.Sigils.GameBattleHooks
import BallCore.NoodleEditor.NoodleEditor

private def suggestGroups(using sql: SQLManager, gm: GroupManager)(
    escaped: Boolean
)(
    context: SuggestionInfo[CommandSender],
    builder: SuggestionsBuilder,
): CompletableFuture[Suggestions] =
    val player = context.sender().asInstanceOf[Player]

    sql.useFuture {
        sql.withS(sql.withTX(gm.userGroups(player.getUniqueId).value))
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
            sql.withS(sql.withTX(gm.userGroups(sender.getUniqueId).value))
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
                trans"commands.groups.argument-not-found".args(group.toComponent)
            )
}

class GroupsCommand(using
    prompts: BallCore.UI.Prompts,
    plugin: Plugin,
    gm: GroupManager,
    cbm: CivBeaconManager,
    sql: SQLManager,
    e: PolyhedraEditor,
    polygons: PolygonEditor,
    primeTime: PrimeTimeManager,
    kv: KeyVal,
    gameBattleHooks: GameBattleHooks,
    noodleEditor: NoodleEditor,
):
    val viewNearbyClaimsNode =
        CommandTree("nearby-claims")
            .executesPlayer({ (sender, args) =>
                sql.useFireAndForget(for {
                    nearbyBeacons <- sql.withS(
                        cbm.beaconsNearby(sender.getLocation())
                    )
                    _ <- IO { polygons.view(sender, nearbyBeacons) }
                } yield ())
            }: PlayerCommandExecutor)
    val cancelBattleNode =
        CommandTree("cancel-battle").`then`(
            TextArgument("group")
                .replaceSuggestions(suggestGroups(true))
                .executesPlayer(withGroupArgument("group") {
                    (sender, args, group) =>
                        sql.useFireAndForget(
                            for {
                                result <- sql.withS(
                                    sql.withTX(
                                        gm
                                            .check(
                                                sender.getUniqueId,
                                                group.id,
                                                nullUUID,
                                                Permissions.ManageClaims,
                                            )
                                            .value
                                    )
                                )
                                _ <- result match
                                    case Right(ok) if ok =>
                                        gameBattleHooks
                                            .cancelImpendingBattle(group.id)
                                    case _ =>
                                        IO {
                                            sender.sendServerMessage(
                                                trans"commands.groups.cancel-battle.error",
                                            )
                                        }

                            } yield ()
                        )

                })
        )
    val inviteNode =
        LiteralArgument("invite")
            .`then`(
                OfflinePlayerArgument("player")
                    .executesPlayer(withGroupArgument("group") { (sender, args, group) =>
                        val target =
                            args.getUnchecked[OfflinePlayer]("player")
                        if target.getName() == null then
                            sender.sendServerMessage(
                                trans"commands.groups.invite.never-joined"
                            )
                        sql.useBlocking(
                            sql.withS(sql.withTX(gm.getGroup(group.id).value))
                        ) match
                            case Left(err) =>
                                sender.sendServerMessage(
                                    trans"commands.groups.invite.couldnt-invite".args(
                                        target.getName().toComponent,
                                        err.explain().toComponent,
                                    )
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
                                            trans"commands.groups.invite.already-joined".args(
                                                target.getName().toComponent,
                                                group.name.toComponent,
                                            )
                                        )
                                    else
                                        sql.useBlocking(
                                            sql.withS(
                                                gm.invites.inviteToGroup(
                                                    sender.getUniqueId,
                                                    target.getUniqueId,
                                                    group.id,
                                                )
                                            )
                                        )
                                        sender.sendServerMessage(
                                            trans"commands.groups.invite.invited".args(
                                                target.getName.toComponent,
                                                group.name.toComponent,
                                            )
                                        )
                                else
                                    sender.sendServerMessage(
                                        trans"commands.groups.invite.no-permission".args(
                                            group.name.toComponent,
                                        )
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
                        val p = BallCore.Groups.GroupManagementProgram()
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
                    val p = BallCore.Groups.GroupListProgram()
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
                    val p = BallCore.Groups.InvitesListProgram()
                    val runner =
                        UIProgramRunner(p, p.Flags(sender.getUniqueId), sender)
                    runner.render()
                }
            }: PlayerCommandExecutor)
