// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Chat

import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.player.{PlayerJoinEvent, PlayerQuitEvent}
import org.bukkit.event.{EventHandler, EventPriority}

class ChatListener(using ca: ChatActor) extends org.bukkit.event.Listener:
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def chatEvent(event: AsyncChatEvent): Unit =
        event.setCancelled(true)
        ca.send(ChatMessage.send(event.getPlayer, event.message()))

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def joinEvent(event: PlayerJoinEvent): Unit =
        event.joinMessage(null)
        ca.send(ChatMessage.joined(event.getPlayer))

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def leaveEvent(event: PlayerQuitEvent): Unit =
        event.quitMessage(null)
        ca.send(ChatMessage.left(event.getPlayer))
