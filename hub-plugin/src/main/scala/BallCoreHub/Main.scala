// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCoreHub

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.{NamedTextColor, TextColor}
import org.bukkit.Bukkit
import org.bukkit.event.player.{PlayerJoinEvent, PlayerQuitEvent}
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.plugin.java.JavaPlugin

class ChatListener extends Listener:
  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  def chatEvent(event: AsyncChatEvent): Unit =
    event.setCancelled(true)
    val message =
      Component
        .text("[Hub] ")
        .append(event.getPlayer.displayName())
        .append(Component.text(": "))
        .append(event.message().color(NamedTextColor.WHITE))
        .color(TextColor.fromHexString("#009356"))
    Bukkit.getServer.sendMessage(message)

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  def joinEvent(event: PlayerJoinEvent): Unit =
    event.joinMessage(null)
    val message =
      event.getPlayer
        .displayName()
        .append(Component.text(" has joined the hub"))
        .color(NamedTextColor.YELLOW)
    Bukkit.getServer.sendMessage(message)

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  def leaveEvent(event: PlayerQuitEvent): Unit =
    event.quitMessage(null)
    val message =
      event.getPlayer
        .displayName()
        .append(Component.text(" has left the hub"))
        .color(NamedTextColor.YELLOW)
    Bukkit.getServer.sendMessage(message)

final class Main extends JavaPlugin:
  override def onEnable(): Unit =
    getServer.getPluginManager.registerEvents(ChatListener(), this)

  override def onDisable(): Unit =
    ()
