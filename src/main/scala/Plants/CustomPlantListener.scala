package BallCore.Plants

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.Material

class CustomPlantListener() extends Listener:
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	def onPlantCrop(event: BlockPlaceEvent): Unit =
		val plant = Plant.values.find { p =>
			p.plant match
				case PlantType.ageable(mat) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.generateTree(mat, kind) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.stemmedAgeable(mat) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.verticalPlant(mat) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.bamboo =>
					Material.BAMBOO == event.getBlockPlaced().getType()
				case PlantType.fruitTree(looksLike, fruit) =>
					false
		}
		event.getPlayer().sendMessage(s"${plant}")
