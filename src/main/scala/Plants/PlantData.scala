package BallCore.Plants

import org.bukkit.Material
import BallCore.Datekeeping.Season
import org.bukkit.TreeType
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import scala.util.chaining._
import org.bukkit.inventory.ItemStack

object PlantData:
	import Season._
	import Climate._
	import Plant._
	import Material._
	import TreeType._

	val plants = List(
		PlantData( generateTree(MANGROVE_PROPAGULE, MANGROVE), warmHumid, spring ),
		PlantData( generateTree(JUNGLE_SAPLING, JUNGLE), warmHumid, spring ),
		PlantData( bamboo, warmHumid, summer ),
		PlantData( generateTree(Material.AZALEA, TreeType.AZALEA), warmHumid, summer ),
		PlantData( generateTree(ACACIA_SAPLING, ACACIA), warmHumid, autumn ),
		PlantData( ageable(COCOA), warmHumid, autumn ),
	//
		PlantData( ageable(CARROTS), warmArid, spring ),
		PlantData( fruitTree(BIG_TREE, peach), warmArid, spring ),
		PlantData( verticalPlant(CACTUS), warmArid, summer ),
		PlantData( ageable(MELON_STEM), warmArid, summer ),
		PlantData( generateTree(OAK_SAPLING, TREE), warmArid, autumn ),
		PlantData( ageable(BEETROOTS), warmArid, autumn ),
	//
		PlantData( ageable(POTATOES), coldHumid, spring ),
		PlantData( verticalPlant(SUGAR_CANE), coldHumid, spring ),
		PlantData( fruitTree(BIG_TREE, plum), coldHumid, summer ),
		PlantData( generateTree(DARK_OAK_SAPLING, DARK_OAK), coldHumid, summer ),
		PlantData( generateTree(SPRUCE_SAPLING, REDWOOD), coldHumid, autumn ),
		PlantData( fruitTree(BIG_TREE, ItemStack(APPLE)), coldHumid, autumn ),
	//
		PlantData( fruitTree(BIG_TREE, apricot), coldArid, spring ),
		PlantData( generateTree(BIRCH_SAPLING, BIRCH), coldArid, spring ),
		PlantData( ageable(SWEET_BERRY_BUSH), coldArid, summer ),
		PlantData( fruitTree(BIG_TREE, pear), coldArid, summer ),
		PlantData( ageable(PUMPKIN_STEM), coldArid, autumn ),
		PlantData( ageable(WHEAT), coldArid, autumn ),
	)

	val apricot = CustomItemStack.make(NamespacedKey("ballcore", "apricot"), Material.APPLE, "&rApricot")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(1))))
	val peach = CustomItemStack.make(NamespacedKey("ballcore", "peach"), Material.APPLE, "&rPeach")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(2))))
	val pear = CustomItemStack.make(NamespacedKey("ballcore", "pear"), Material.APPLE, "&rPear")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(3))))
	val plum = CustomItemStack.make(NamespacedKey("ballcore", "plum"), Material.APPLE, "&rPlum")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(4))))

enum Climate:
	case warmArid
	case warmHumid
	case coldArid
	case coldHumid

// divided by how we're supposed to grow the plant
enum Plant:
	case ageable(mat: Material)
	case generateTree(mat: Material, kind: TreeType)
	case stemmedAgeable(mat: Material)
	// sugarcane and cacti
	case verticalPlant(mat: Material)
	case bamboo
	case fruitTree(looksLike: TreeType, fruit: ItemStack)

case class PlantData(
	val plant: Plant,
	val growingClimate: Climate,
	val growingSeason: Season,
)
