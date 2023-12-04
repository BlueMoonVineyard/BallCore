package BallCore.SpawnInventory

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin

object Listener:
    def register()(using p: Plugin): Unit =
        p.getServer().getPluginManager().registerEvents(Listener(), p)

class Listener() extends org.bukkit.event.Listener:

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onPlayerJoin(event: PlayerJoinEvent): Unit =
        val player = event.getPlayer()
        if player.hasPlayedBefore() then return ()
        InventorySetter.giveSpawnInventory(player)
