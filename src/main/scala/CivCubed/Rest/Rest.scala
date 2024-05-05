package CivCubed.Rest

import CivCubed.Storage.SQLManager
import org.bukkit.plugin.Plugin

object Rest:
    def register()(using rm: RestManager, p: Plugin, sql: SQLManager): Unit =
        p.getServer.getPluginManager.registerEvents(RestListener(), p)
