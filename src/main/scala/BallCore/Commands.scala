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
import BallCore.OneTimeTeleport.OneTimeTeleporter
import dev.jorel.commandapi.arguments.PlayerArgument
import BallCore.OneTimeTeleport.OTTError
import net.kyori.adventure.text.Component
import cats.effect.IO
import dev.jorel.commandapi.executors.CommandExecutor
import BallCore.RandomSpawner.RandomSpawn
import BallCore.SpawnInventory.InventorySetter
import BallCore.Plants.Climate
import BallCore.SpawnInventory.OresAndYou
import cats.data.OptionT
import BallCore.Beacons.HeartBlock
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import dev.jorel.commandapi.arguments.AdventureChatArgument
import BallCore.SpawnInventory.HeartsAndYou
import BallCore.Storage.KeyVal
import dev.jorel.commandapi.arguments.IntegerArgument
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import java.util.concurrent.TimeUnit

class OTTCommand(using sql: SQLManager, ott: OneTimeTeleporter):
    private def errorText(err: OTTError): Component =
        err match
            case OTTError.alreadyUsedTeleport =>
                txt"You've already used your one-time teleport."
            case OTTError.isNotTeleportingToYou =>
                txt"That player isn't teleporting to you."
            case OTTError.teleportFailed =>
                txt"The teleport failed."

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
                                                    txt"/ott accept ${sender.getName()}"
                                                        .color(Colors.teal)
                                                target.sendServerMessage(
                                                    txt"${sender.displayName()} has sent you a one-time teleport request. Use $command to accept it."
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

class GetHeart:
    val node =
        CommandTree("get-heart")
            .executesPlayer({ (sender, args) =>
                val _ =
                    sender.getInventory().addItem(HeartBlock.itemStack.clone())
            }: PlayerCommandExecutor)

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
                                        sender.sendServerMessage(
                                            txt"Bound ${group.name} to your Civilization Beacon!"
                                        )
                            }
                        } yield ())
                    })
            )

class OneTimeAdaptation(using
    sql: SQLManager,
    kv: KeyVal,
    as: Acclimation.Storage,
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

class BookCommand(using
    storage: BallCore.Acclimation.Storage,
    sql: SQLManager,
    p: Plugin,
):
    val node =
        CommandTree("book")
            .`then`(
                LiteralArgument("ores-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- sql.withS(OresAndYou.viewForPlayer(sender))
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("hearts-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- HeartsAndYou.viewForPlayer(sender)
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("spawnbook")
                    .executesPlayer({ (sender, args) =>
                        sender.openBook(SpawnInventory.Book.book)
                    }: PlayerCommandExecutor)
            )

class InformationGiver():
    private val informations = List(
        txt"[CivCubed] Consider helping us keep the lights on by donating to https://opencollective.net/civcubed!",
        txt"[CivCubed] Browse the selection of ${txt("/book").color(Colors.teal)} and learn more about the server!",
        txt"[CivCubed] Rest accumulates when you log off and come back the next day!",
        txt"[CivCubed] See what plants grow in your area with ${txt("/plants").color(Colors.teal)}!",
        txt"[CivCubed] The more time you spend somewhere, the more ores you'll get when you mine!",
        txt"[CivCubed] Join the Discord at https://discord.civcubed.net!",
    ).map(_.replaceText(ChatActor.urlReplacer))
    private var informationCounter = 0

    private def sendInformation(): Unit =
        Bukkit.getServer().sendServerMessage(informations(informationCounter))
        informationCounter = (informationCounter + 1) % informations.size

    def register()(using p: Plugin): Unit =
        val _ = p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => this.sendInformation(), 1, 13, TimeUnit.MINUTES)

class RestartTimer():
    private var duration: Int = -1
    private var time: Int = -1
    private var ticking: Boolean = false
    private var bossBar: BossBar = null

    private def tick(): Unit =
        if ticking && bossBar != null then
            time = time - 1
            if time <= 0 then
                bossBar.progress(1.0f)
                bossBar.color(BossBar.Color.RED)
                val _ = bossBar.name(txt"Restarting...")
            else
                bossBar.progress(time.toFloat / duration.toFloat)
                val _ = bossBar.name(
                    txt"Restart Timer: You will be sent to the hub in ${time} seconds"
                )

    private val node =
        CommandTree("timer")
            .withRequirement(_.hasPermission("ballcore.timer"))
            .`then`(
                LiteralArgument("start")
                    .`then`(
                        IntegerArgument("seconds", 1)
                            .executesPlayer({ (sender, args) =>
                                val seconds = args
                                    .getUnchecked[Integer]("seconds")
                                    .intValue()
                                time = seconds
                                duration = seconds

                                if bossBar != null then
                                    val _ =
                                        bossBar.removeViewer(Bukkit.getServer())

                                bossBar = BossBar.bossBar(
                                    txt"Restart Timer: You will be sent to the hub in ${time} seconds",
                                    1.0f,
                                    BossBar.Color.GREEN,
                                    BossBar.Overlay.PROGRESS,
                                )
                                val _ = bossBar.addViewer(Bukkit.getServer())

                                ticking = true
                            }: PlayerCommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("stop")
                    .executesPlayer({ (sender, args) =>
                        if bossBar != null then
                            val _ = bossBar.removeViewer(Bukkit.getServer())
                        ticking = false
                    }: PlayerCommandExecutor)
            )

    def register()(using p: Plugin): Unit =
        node.register()
        val _ = p
            .getServer()
            .getAsyncScheduler()
            .runAtFixedRate(p, _ => this.tick(), 1, 1, TimeUnit.SECONDS)

class CheatCommand(using
    registry: ItemRegistry,
    pbm: PlantBatchManager,
    aa: AcclimationActor,
    storage: BallCore.Acclimation.Storage,
    sql: SQLManager,
    rs: RandomSpawn,
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
                LiteralArgument("spawn-inventory")
                    .executesPlayer({ (sender, args) =>
                        InventorySetter.giveSpawnInventory(sender)
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("spawnbook")
                    .executesPlayer({ (sender, args) =>
                        sender.openBook(SpawnInventory.Book.book)
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("random-spawn")
                    .`then`(
                        PlayerArgument("player")
                            .executes({ (sender, args) =>
                                val target = args.getUnchecked[Player]("player")
                                sql.useFireAndForget(
                                    rs.randomSpawnLocation.flatMap { location =>
                                        IO {
                                            target.teleportAsync(
                                                location.getLocation()
                                            )
                                        }
                                    }
                                )
                            }: CommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("tick-plants")
                    .`then`(
                        IntegerArgument("amount")
                            .executesPlayer({ (sender, args) =>
                                for _ <- 1 to args.getUnchecked[Integer]("amount").intValue() do
                                    pbm.send(PlantMsg.tickPlants)
                                    sender.sendServerMessage(
                                        txt"An hour of ingame time has passed"
                                    )
                            }: PlayerCommandExecutor)
                    )
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
                            sql.useBlocking(sql.withS(for {
                                elevation <- storage.getElevation(uuid)
                                latitude <- storage.getLatitude(uuid)
                                longitude <- storage.getLongitude(uuid)
                                temperature <- storage.getTemperature(uuid)
                            } yield (elevation, latitude, longitude, temperature)))
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
                        sql.useBlocking(
                            sql.withS(sql.withTX(gm.getGroup(group.id).value))
                        ) match
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
                                            sql.withS(
                                                gm.invites.inviteToGroup(
                                                    sender.getUniqueId,
                                                    target.getUniqueId,
                                                    group.id,
                                                )
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
                    val climate = Climate.climateAt(
                        sender.getX().toInt,
                        sender.getY().toInt,
                        sender.getZ().toInt,
                    )
                    val p = PlantListProgram()
                    val runner = UIProgramRunner(p, p.Flags(climate), sender)
                    runner.render()
                }
            }: PlayerCommandExecutor)

class CancelCommand(using
    editor: PolygonEditor,
    polyhedraEditor: PolyhedraEditor,
):
    val node =
        CommandTree("cancel")
            .executesPlayer({ (sender, args) =>
                val plr = sender.asInstanceOf[Player]
                editor.cancel(plr)
                polyhedraEditor.cancel(plr)
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
    editor: PolygonEditor
):
    val node =
        CommandTree("declare")
            .executesPlayer({ (sender, args) =>
                editor.declare(sender)
            }: PlayerCommandExecutor)

class MessageCommand(using ca: ChatActor):
    val meNode =
        CommandTree("me")
            .`then`(
                AdventureChatArgument("message")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage.sendMe(
                                sender,
                                args.getUnchecked[Component]("message"),
                            )
                        )
                    }: PlayerCommandExecutor)
            )

    val node =
        CommandTree("msg")
            .withAliases("w", "whisper", "message")
            .`then`(
                PlayerArgument("player")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage.chattingWithPlayer(
                                sender,
                                args.getUnchecked[Player]("player"),
                            )
                        )
                    }: PlayerCommandExecutor)
                    .`then`(
                        AdventureChatArgument("message")
                            .executesPlayer({ (sender, args) =>
                                ca.send(
                                    ChatMessage.sendToPlayer(
                                        sender,
                                        args.getUnchecked[Component]("message"),
                                        args.getUnchecked[Player]("player"),
                                    )
                                )
                            }: PlayerCommandExecutor)
                    )
            )

    val replyNode =
        CommandTree("reply")
            .withAliases("r")
            .`then`(
                AdventureChatArgument("message")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage.replyToPlayer(
                                sender,
                                args.getUnchecked[Component]("message"),
                            )
                        )
                    }: PlayerCommandExecutor)
            )

class GammaCommand():
    val node =
        CommandTree("gamma")
            .withAliases("fullbright")
            .`then`(
                LiteralArgument("on")
                    .executesPlayer({ (sender, args) =>
                        val _ = sender.addPotionEffect(
                            PotionEffect(
                                PotionEffectType.NIGHT_VISION,
                                PotionEffect.INFINITE_DURATION,
                                0,
                                true,
                                false,
                            )
                        )
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("off")
                    .executesPlayer({ (sender, args) =>
                        sender.removePotionEffect(PotionEffectType.NIGHT_VISION)
                    }: PlayerCommandExecutor)
            )
class ChatCommands(using ca: ChatActor, gm: GroupManager, sql: SQLManager):
    val group =
        CommandTree("group")
            .withAliases("g")
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
            .withAliases("l")
            .executesPlayer({ (sender, args) =>
                ca.send(ChatMessage.chattingInLocal(sender))
            }: PlayerCommandExecutor)
