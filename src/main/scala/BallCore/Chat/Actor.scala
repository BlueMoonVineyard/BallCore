// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Chat

import BallCore.DataStructures.Actor
import BallCore.Groups.{GroupID, GroupManager}
import BallCore.Storage.SQLManager
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.{
    NamedTextColor,
    TextColor,
    TextDecoration,
}
import net.kyori.adventure.text.{Component, TextReplacementConfig}
import org.bukkit.Bukkit
import org.bukkit.entity.Player

import java.util.regex.Pattern
import scala.jdk.CollectionConverters.*
import scala.collection.concurrent.TrieMap
import com.github.tommyettinger.colorful.pure.oklab.ColorTools
import scala.util.Random
import BallCore.Sidebar.SidebarActor
import BallCore.Sidebar.SidebarMsg
import BallCore.Sidebar.SidebarLine
import cats.effect.IO

enum ChatMessage:
    case send(p: Player, m: Component)
    case sendMe(p: Player, m: Component)

    case sendToGroup(from: Player, m: Component, group: GroupID)
    case sendToGlobal(from: Player, m: Component)
    case sendToPlayer(from: Player, m: Component, target: Player)
    case sendToLocal(from: Player, m: Component)

    case replyToPlayer(from: Player, m: Component)

    case joined(p: Player)
    case left(p: Player)

    case chattingInGroup(p: Player, group: GroupID)
    case chattingInGlobal(p: Player)
    case chattingInLocal(p: Player)
    case chattingWithPlayer(p: Player, target: Player)

enum PlayerState:
    case globalChat
    case localChat
    case groupChat(group: GroupID)
    case chattingWith(target: Player)

object ChatActor:
    val urlRegex: Pattern = Pattern.compile(
        "https?:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)"
    )
    private val urlColor: TextColor = TextColor.fromHexString("#2aa1bf")
    val urlReplacer: TextReplacementConfig =
        TextReplacementConfig
            .builder()
            .`match`(urlRegex)
            .replacement(c =>
                c.clickEvent(ClickEvent.openUrl(c.content()))
                    .decoration(TextDecoration.UNDERLINED, true)
                    .color(urlColor)
            )
            .build()

class ChatActor(using gm: GroupManager, sql: SQLManager, sidebar: SidebarActor)
    extends Actor[ChatMessage]:

    import BallCore.UI.ChatElements.*

    var states: Map[Player, PlayerState] =
        Map[Player, PlayerState]().withDefaultValue(PlayerState.globalChat)
    private val globalGrey: TextColor = TextColor.fromHexString("#686b6f")
    private def groupColor(name: String): TextColor =
        val rand = Random(name.hashCode())
        val range = 0.2f
        val a = rand.between(0.5f - range, 0.5f + range)
        val b = rand.between(0.5f - range, 0.5f + range)
        val oklab = ColorTools.oklab(0.6f, a, b, 1.0f)
        TextColor.color(
            ColorTools.red(oklab),
            ColorTools.green(oklab),
            ColorTools.blue(oklab),
        )
    private val localGrey: TextColor = TextColor.fromHexString("#b6b9bd")
    private val whisperColor: TextColor = TextColor.fromHexString("#ff8255")

    private val playerReplies = TrieMap[Player, Player]()

    protected def handleInit(): Unit =
        ()

    protected def handleShutdown(): Unit =
        ()

    private def preprocess(playerMessage: Component): Component =
        playerMessage.replaceText(ChatActor.urlReplacer)

    private def dm(
        from: Player,
        to: Player,
        msg: Component,
        isMe: Boolean,
    ): Unit =
        if isMe then
            to.sendMessage(
                trans"chat.whisper.recipient.me"
                    .args(from.displayName(), msg.color(NamedTextColor.WHITE))
                    .color(whisperColor)
            )
            from.sendMessage(
                trans"chat.whisper.sender.me"
                    .args(from.displayName(), msg.color(NamedTextColor.WHITE))
                    .color(whisperColor)
            )
        else
            to.sendMessage(
                trans"chat.whisper.recipient"
                    .args(from.displayName(), msg.color(NamedTextColor.WHITE))
                    .color(whisperColor)
            )
            from.sendMessage(
                trans"chat.whisper.sender"
                    .args(from.displayName(), msg.color(NamedTextColor.WHITE))
                    .color(whisperColor)
            )
        playerReplies(to) = from

    private def global(from: Player, msg: Component, isMe: Boolean): Unit =
        if isMe then
            Bukkit.getServer
                .sendMessage(
                    trans"chat.global.me"
                        .args(
                            from.displayName(),
                            msg.color(NamedTextColor.WHITE),
                        )
                        .color(globalGrey)
                )
        else
            Bukkit.getServer
                .sendMessage(
                    trans"chat.global"
                        .args(
                            from.displayName(),
                            msg.color(NamedTextColor.WHITE),
                        )
                        .color(globalGrey)
                )

    private def local(from: Player, msg: Component, isMe: Boolean): Unit =
        val nearby = Bukkit.getOnlinePlayers.asScala
            .filter { plr =>
                plr.getWorld == from.getWorld
            }
            .filter { plr =>
                plr.getLocation()
                    .distanceSquared(
                        from.getLocation()
                    ) < 500 * 500
            }
            .asJava
        if isMe then
            Audience
                .audience(nearby)
                .sendMessage(
                    trans"chat.local.me"
                        .args(
                            from.displayName(),
                            msg.color(NamedTextColor.WHITE),
                        )
                        .color(localGrey)
                )
        else
            Audience
                .audience(nearby)
                .sendMessage(
                    trans"chat.local"
                        .args(
                            from.displayName(),
                            msg.color(NamedTextColor.WHITE),
                        )
                        .color(localGrey)
                )

    private def sendGroup(
        from: Player,
        msg: Component,
        group: GroupID,
        isMe: Boolean,
    ): Unit =
        val audience = sql
            .useBlocking {
                sql.withS(
                    sql.withTX(gm.groupAudience(group).value)
                )
            }

        if isMe then
            audience.foreach { (name, aud) =>
                aud.sendMessage(
                    trans"chat.group.me"
                        .args(
                            name.toComponent,
                            from.displayName(),
                            msg.color(NamedTextColor.WHITE),
                        )
                        .color(groupColor(name))
                )
            }
        else
            audience.foreach { (name, aud) =>
                aud.sendMessage(
                    trans"chat.group"
                        .args(
                            name.toComponent,
                            from.displayName(),
                            msg.color(NamedTextColor.WHITE),
                        )
                        .color(groupColor(name))
                )
            }

    private def updateChannel(p: Player, c: PlayerState): Unit =
        val update = (msg: Component) =>
            sidebar.send(
                SidebarMsg.update(
                    SidebarLine.chatChannel,
                    p,
                    Some(msg.color(NamedTextColor.GREEN)),
                )
            )
        c match
            case PlayerState.globalChat => update(trans"chat.sidebar.global")
            case PlayerState.localChat => update(trans"chat.sidebar.local")
            case PlayerState.groupChat(group) =>
                sql.useFireAndForget(for {
                    audience <- sql.withS(
                        sql.withTX(gm.groupAudience(group).value)
                    )
                    _ <- audience match
                        case Left(err) =>
                            IO { update(trans"chat.sidebar.group.unknown") }
                        case Right((name, _)) =>
                            IO {
                                update(
                                    trans"chat.sidebar.group".args(
                                        name.toComponent
                                    )
                                )
                            }
                } yield ())
            case PlayerState.chattingWith(target) =>
                update(trans"chat.sidebar.dms".args(target.displayName))

    def handle(m: ChatMessage): Unit =
        m match
            case ChatMessage.send(p, m_) =>
                val m = preprocess(m_)
                states(p) match
                    case PlayerState.globalChat =>
                        global(p, m, false)
                    case PlayerState.localChat =>
                        local(p, m, false)
                    case PlayerState.groupChat(group) =>
                        sendGroup(p, m, group, false)
                    case PlayerState.chattingWith(target) =>
                        dm(p, target, m, false)
            case ChatMessage.sendMe(p, m_) =>
                val m = preprocess(m_)
                states(p) match
                    case PlayerState.globalChat =>
                        global(p, m, true)
                    case PlayerState.localChat =>
                        local(p, m, true)
                    case PlayerState.groupChat(group) =>
                        sendGroup(p, m, group, true)
                    case PlayerState.chattingWith(target) =>
                        dm(p, target, m, true)
            case ChatMessage.joined(p) =>
                Bukkit.getServer
                    .sendMessage(
                        trans"chat.notification.join"
                            .args(p.displayName())
                            .color(NamedTextColor.YELLOW)
                    )
                updateChannel(p, states(p))
            case ChatMessage.left(p) =>
                Bukkit.getServer
                    .sendMessage(
                        trans"chat.notification.leave"
                            .args(p.displayName())
                            .color(NamedTextColor.YELLOW)
                    )
            case ChatMessage.chattingInGroup(p, group) =>
                states += p -> PlayerState.groupChat(group)
                p.sendMessage(
                    trans"chat.notification.switch.group"
                        .args(p.displayName())
                        .color(NamedTextColor.GREEN)
                )
                updateChannel(p, states(p))
            case ChatMessage.chattingInGlobal(p) =>
                states += p -> PlayerState.globalChat
                p.sendMessage(
                    trans"chat.notification.switch.global"
                        .args(p.displayName())
                        .color(NamedTextColor.GREEN)
                )
                updateChannel(p, states(p))
            case ChatMessage.chattingInLocal(p) =>
                states += p -> PlayerState.localChat
                p.sendMessage(
                    trans"chat.notification.switch.local"
                        .args(p.displayName())
                        .color(NamedTextColor.GREEN)
                )
                updateChannel(p, states(p))
            case ChatMessage.sendToPlayer(from, m, target) =>
                dm(from, target, m, false)
            case ChatMessage.sendToGroup(from, m, group) =>
                sendGroup(from, m, group, false)
            case ChatMessage.sendToLocal(from, m) =>
                local(from, m, false)
            case ChatMessage.sendToGlobal(from, m) =>
                global(from, m, false)
            case ChatMessage.replyToPlayer(from, m) =>
                playerReplies.get(from) match
                    case None =>
                        from.sendServerMessage(
                            trans"chat.notification.no-reply"
                                .color(NamedTextColor.GREEN)
                        )
                    case Some(target) =>
                        dm(from, target, m, false)
            case ChatMessage.chattingWithPlayer(from, target) =>
                states += from -> PlayerState.chattingWith(target)
                from.sendMessage(
                    trans"chat.notification.switch.dm"
                        .args(target.displayName())
                        .color(NamedTextColor.GREEN)
                )
                updateChannel(from, states(from))
