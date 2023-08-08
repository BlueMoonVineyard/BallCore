// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCoreHub

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.EventPriority
import org.bukkit.Bukkit
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.NamedTextColor

class ChatListener extends Listener:
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def chatEvent(event: AsyncChatEvent): Unit =
        event.setCancelled(true)
        val message =
            Component.text("[Hub] ")
                .append(event.getPlayer().displayName())
                .append(Component.text(": "))
                .append(event.message().color(NamedTextColor.WHITE))
                .color(TextColor.fromHexString("#009356"))
        Bukkit.getServer().sendMessage(message)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def joinEvent(event: PlayerJoinEvent): Unit =
        event.joinMessage(null)
        val message =
            event.getPlayer().displayName()
                .append(Component.text(" has joined the hub"))
                .color(NamedTextColor.YELLOW)
        Bukkit.getServer().sendMessage(message)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def leaveEvent(event: PlayerQuitEvent): Unit =
        event.quitMessage(null)
        val message =
            event.getPlayer().displayName()
                .append(Component.text(" has left the hub"))
                .color(NamedTextColor.YELLOW)
        Bukkit.getServer().sendMessage(message)

final class Main extends JavaPlugin:
    override def onEnable() =
        getServer().getPluginManager().registerEvents(ChatListener(), this)
    override def onDisable() =
        ()
