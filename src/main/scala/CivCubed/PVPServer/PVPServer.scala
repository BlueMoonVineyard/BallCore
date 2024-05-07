package CivCubed.PVPServer

import org.bukkit.plugin.Plugin
import CivCubed.Storage.SQLManager

def register()(using p: Plugin, sql: SQLManager): Unit =
    ArmorStandDispenser.register()
    ItemFrameDispenser.register()
    Loadouts.register()
