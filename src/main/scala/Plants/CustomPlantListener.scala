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

class CustomPlantListener() extends Listener:
	var uwu = List[PlantData]()

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	def onPlantCrop(event: BlockPlaceEvent): Unit =
		val plant = Plant.values.find { p =>
			p.plant match
				case PlantType.ageable(mat) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.generateTree(mat, kind) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.stemmedAgeable(stem, fruit) =>
					stem == event.getBlockPlaced().getType()
				case PlantType.verticalPlant(mat) =>
					mat == event.getBlockPlaced().getType()
				case PlantType.bamboo =>
					Material.BAMBOO == event.getBlockPlaced().getType()
				case PlantType.fruitTree(looksLike, fruit) =>
					false
		}
		plant match
			case None =>
			case Some(what) =>
				uwu = uwu appended PlantData(what, event.getBlock(), Instant.now())

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	def onDebugRightClick(event: PlayerInteractEvent): Unit =
		uwu.find(_.where == event.getClickedBlock()) match
			case None =>
			case Some(value) =>
				value.what.plant match
					case PlantType.ageable(mat) =>
						value.where.setBlockData(
							value.where.getBlockData().asInstanceOf[Ageable].tap(x => x.setAge(x.getAge() + 1)), true
						)
					case PlantType.generateTree(mat, kind) =>
						val block = event.getClickedBlock()
						val consumer: Consumer[BlockState] = state => {}
						block.setType(Material.AIR, true)
						block.getWorld().generateTree(block.getLocation(), java.util.Random(), kind, consumer)
					case PlantType.stemmedAgeable(stem, fruit) =>
						val ageable = value.where.getBlockData().asInstanceOf[Ageable]
						ageable.setAge(ageable.getAge() + 1)
						value.where.setBlockData(ageable)
						if ageable.getAge() == ageable.getMaximumAge() then
							event.getClickedBlock().setType(fruit, true)
					case PlantType.verticalPlant(mat) =>
						???
					case PlantType.bamboo =>
						???
					case PlantType.fruitTree(looksLike, fruit) =>
						???
				
		
