package BallCore.Rest

import BallCore.Storage.SQLManager
import org.bukkit.plugin.Plugin

object Rest:
    def register()(using rm: RestManager, p: Plugin, sql: SQLManager): Unit =
        p.getServer.getPluginManager.registerEvents(RestListener(), p)
