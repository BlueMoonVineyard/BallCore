package BallCore.Plants

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.Material
import java.time.Instant
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.block.data.Ageable
import scala.util.chaining._
import org.bukkit.BlockChangeDelegate
import org.bukkit.block.data.BlockData
import scala.util.Random
import org.bukkit.util.Consumer
import org.bukkit.block.BlockState
import org.bukkit.block.BlockFace

class CustomPlantListener()(using pbm: PlantBatchManager) extends Listener:
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	def onPlantCrop(event: BlockPlaceEvent): Unit =
		val plant = Plant.values.find { p =>
			p.plant match
				case PlantType.ageable(mat, _) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.generateTree(mat, kind, _) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.stemmedAgeable(stem, fruit, _) =>
					stem == event.getBlockPlaced().getType()
				case PlantType.verticalPlant(mat, _) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.bamboo(_) =>
					Material.BAMBOO == event.getBlockPlaced().getType()
				case PlantType.fruitTree(looksLike, fruit, _) =>
					false
		}
		plant match
			case None =>
			case Some(what) =>
				pbm.send(PlantMsg.startGrowing(what, event.getBlock()))

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	def onDebugRightClick(event: PlayerInteractEvent): Unit =
		pbm.send(PlantMsg.tickPlants)

