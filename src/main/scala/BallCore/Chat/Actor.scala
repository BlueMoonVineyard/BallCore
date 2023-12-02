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

enum ChatMessage:
    case send(p: Player, m: Component)
    case sendToPlayer(from: Player, m: Component, target: Player)

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

class ChatActor(using gm: GroupManager, sql: SQLManager)
    extends Actor[ChatMessage]:

    import BallCore.UI.ChatElements.*

    var states: Map[Player, PlayerState] =
        Map[Player, PlayerState]().withDefaultValue(PlayerState.globalChat)
    private val globalGrey: TextColor = TextColor.fromHexString("#686b6f")
    private val groupGrey: TextColor = TextColor.fromHexString("#9b9ea2")
    private val localGrey: TextColor = TextColor.fromHexString("#b6b9bd")
    private val urlColor: TextColor = TextColor.fromHexString("#2aa1bf")
    private val whisperColor: TextColor = TextColor.fromHexString("#ff8255")

    private val playerReplies = TrieMap[Player, Player]()

    private val urlRegex: Pattern = Pattern.compile(
        "https?:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)"
    )
    private val urlReplacer: TextReplacementConfig =
        TextReplacementConfig
            .builder()
            .`match`(urlRegex)
            .replacement(c =>
                c.clickEvent(ClickEvent.openUrl(c.content()))
                    .decoration(TextDecoration.UNDERLINED, true)
                    .color(urlColor)
            )
            .build()

    protected def handleInit(): Unit =
        ()

    protected def handleShutdown(): Unit =
        ()

    private def preprocess(playerMessage: Component): Component =
        playerMessage.replaceText(urlReplacer)

    private def dm(from: Player, to: Player, msg: Component): Unit =
        to.sendMessage(
            txt"[DMs] from ${from.displayName()}: ${msg.color(NamedTextColor.WHITE)}".color(whisperColor)
        )
        from.sendMessage(
            txt"[DMs] to ${to.displayName()}: ${msg.color(NamedTextColor.WHITE)}".color(whisperColor)
        )
        playerReplies(to) = from

    def handle(m: ChatMessage): Unit =
        m match
            case ChatMessage.send(p, m_) =>
                val m = preprocess(m_)
                states(p) match
                    case PlayerState.globalChat =>
                        Bukkit.getServer
                            .sendMessage(
                                txt"[!] ${p.displayName()}: ${m.color(NamedTextColor.WHITE)}"
                                    .color(globalGrey)
                            )
                    case PlayerState.localChat =>
                        val nearby = Bukkit.getOnlinePlayers.asScala
                            .filter { plr =>
                                plr.getWorld == p.getWorld
                            }
                            .filter { plr =>
                                plr.getLocation()
                                    .distanceSquared(
                                        p.getLocation()
                                    ) < 500 * 500
                            }
                            .asJava
                        Audience
                            .audience(nearby)
                            .sendMessage(
                                txt"[Local] ${p.displayName()}: ${m.color(NamedTextColor.WHITE)}"
                                    .color(localGrey)
                            )
                    case PlayerState.groupChat(group) =>
                        sql
                            .useBlocking {
                                sql.withS(
                                    sql.withTX(gm.groupAudience(group).value)
                                )
                            }
                            .foreach { (name, aud) =>
                                aud.sendMessage(
                                    txt"[$name] ${p.displayName()}: ${m.color(NamedTextColor.WHITE)}"
                                        .color(groupGrey)
                                )
                            }
                    case PlayerState.chattingWith(target) =>
                        dm(p, target, m)
            case ChatMessage.joined(p) =>
                Bukkit.getServer
                    .sendMessage(
                        txt"${p.displayName()} has joined the game"
                            .color(NamedTextColor.YELLOW)
                    )
            case ChatMessage.left(p) =>
                Bukkit.getServer
                    .sendMessage(
                        txt"${p.displayName()} has left the game"
                            .color(NamedTextColor.YELLOW)
                    )
            case ChatMessage.chattingInGroup(p, group) =>
                states += p -> PlayerState.groupChat(group)
                p.sendMessage(
                    txt"You are now chatting in a group".color(
                        NamedTextColor.GREEN
                    )
                )
            case ChatMessage.chattingInGlobal(p) =>
                states += p -> PlayerState.globalChat
                p.sendMessage(
                    txt"You are now chatting in global chat".color(
                        NamedTextColor.GREEN
                    )
                )
            case ChatMessage.chattingInLocal(p) =>
                states += p -> PlayerState.localChat
                p.sendMessage(
                    txt"You are now chatting in local chat".color(
                        NamedTextColor.GREEN
                    )
                )
            case ChatMessage.sendToPlayer(from, m, target) =>
                dm(from, target, m)
            case ChatMessage.replyToPlayer(from, m) =>
                playerReplies.get(from) match
                    case None =>
                        from.sendServerMessage(txt"You have nobody to reply to.")
                    case Some(target) =>
                        dm(from, target, m)
            case ChatMessage.chattingWithPlayer(from, target) =>
                states += from -> PlayerState.chattingWith(target)
                from.sendMessage(
                    txt"You are now chatting with ${target.displayName()}".color(
                        NamedTextColor.GREEN
                    )
                )
