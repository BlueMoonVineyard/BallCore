package BallCore.Plants

import org.bukkit.plugin.Plugin
import BallCore.CustomItems.ItemRegistry
import BallCore.Storage.SQLManager
import BallCore.DataStructures.ShutdownCallbacks

object Plants:
	def register()(using p: Plugin, reg: ItemRegistry, sql: SQLManager, sm: ShutdownCallbacks): PlantBatchManager =
		reg.register(Fruit(Fruit.apricot))
		reg.register(Fruit(Fruit.peach))
		reg.register(Fruit(Fruit.pear))
		reg.register(Fruit(Fruit.plum))
		given pbm: PlantBatchManager = PlantBatchManager()
		pbm.startListener()
		p.getServer().getPluginManager().registerEvents(VanillaPlantBlocker(), p)
		p.getServer().getPluginManager().registerEvents(CustomPlantListener(), p)
		return pbm
