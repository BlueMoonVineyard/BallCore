package BallCore.Rest

import BallCore.Storage.SQLManager
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class RestListener()(using rm: RestManager, sql: SQLManager) extends Listener:

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onLogon(event: PlayerJoinEvent): Unit =
        sql.useFireAndForget(
            sql.withS(rm.logon(event.getPlayer().getUniqueId()))
        )

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onLogoff(event: PlayerQuitEvent): Unit =
        sql.useFireAndForget(
            sql.withS(rm.logoff(event.getPlayer().getUniqueId()))
        )
