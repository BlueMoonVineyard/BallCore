package CivCubed.PVPServer

import org.bukkit.plugin.Plugin

def register()(using p: Plugin): Unit =
    ArmorStandDispenser.register()
    ItemFrameDispenser.register()
