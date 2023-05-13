package BallCore.Plants

import org.bukkit.plugin.Plugin
import BallCore.CustomItems.ItemRegistry

object Plants:
	def register()(using p: Plugin, reg: ItemRegistry): Unit =
		reg.register(Fruit(PlantData.apricot))
		reg.register(Fruit(PlantData.peach))
		reg.register(Fruit(PlantData.pear))
		reg.register(Fruit(PlantData.plum))
		p.getServer().getPluginManager().registerEvents(VanillaPlantBlocker(), p)
