package BallCore.Plants

import org.bukkit.plugin.Plugin
import BallCore.CustomItems.ItemRegistry

object Plants:
	def register()(using p: Plugin, reg: ItemRegistry): Unit =
		p.getServer().getPluginManager().registerEvents(VanillaPlantBlocker(), p)
