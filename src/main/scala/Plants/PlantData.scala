package BallCore.Plants

import org.bukkit.Material
import BallCore.Datekeeping.Season
import org.bukkit.TreeType
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import scala.util.chaining._
import org.bukkit.inventory.ItemStack
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.ItemGroup
import org.bukkit.block.Block
import java.time.Instant

object Fruit:
	val fruits = ItemGroup(NamespacedKey("ballcore", "fruits"), ItemStack(Material.APPLE))
	val apricot = CustomItemStack.make(NamespacedKey("ballcore", "apricot"), Material.APPLE, "&rApricot")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(1))))
	val peach = CustomItemStack.make(NamespacedKey("ballcore", "peach"), Material.APPLE, "&rPeach")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(2))))
	val pear = CustomItemStack.make(NamespacedKey("ballcore", "pear"), Material.APPLE, "&rPear")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(3))))
	val plum = CustomItemStack.make(NamespacedKey("ballcore", "plum"), Material.APPLE, "&rPlum")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(4))))

class Fruit(val what: CustomItemStack) extends CustomItem:
	def group = Fruit.fruits
	def template = what

enum Climate:
	case warmArid
	case warmHumid
	case coldArid
	case coldHumid

// divided by how we're supposed to grow the plant
enum PlantType:
	case ageable(seed: Material)
	case generateTree(sapling: Material, kind: TreeType)
	case stemmedAgeable(stem: Material, fruit: Material)
	// sugarcane and cacti
	case verticalPlant(what: Material)
	case bamboo
	case fruitTree(looksLike: TreeType, fruit: ItemStack)

case class PlantData(
	val what: Plant,
	val where: Block,
	val howOld: Instant,
)

enum Plant(
	val plant: PlantType,
	val growingClimate: Climate,
	val growingSeason: Season,
):
	case Mangrove extends Plant( PlantType.generateTree(Material.MANGROVE_PROPAGULE, TreeType.MANGROVE), Climate.warmHumid, Season.spring )
	case Jungle extends Plant( PlantType.generateTree(Material.JUNGLE_SAPLING, TreeType.JUNGLE), Climate.warmHumid, Season.spring )
	case Bamboo extends Plant( PlantType.bamboo, Climate.warmHumid, Season.summer )
	case Azalea extends Plant( PlantType.generateTree(Material.AZALEA, TreeType.AZALEA), Climate.warmHumid, Season.summer )
	case Acacia extends Plant( PlantType.generateTree(Material.ACACIA_SAPLING, TreeType.ACACIA), Climate.warmHumid, Season.autumn )
	case Cocoa extends Plant( PlantType.ageable(Material.COCOA), Climate.warmHumid, Season.autumn )

	case Carrot extends Plant( PlantType.ageable(Material.CARROTS), Climate.warmArid, Season.spring )
	case Peach extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.peach), Climate.warmArid, Season.spring )
	case Cactus extends Plant( PlantType.verticalPlant(Material.CACTUS), Climate.warmArid, Season.summer )
	case Melon extends Plant( PlantType.stemmedAgeable(Material.MELON_STEM, Material.MELON), Climate.warmArid, Season.summer )
	case Oak extends Plant( PlantType.generateTree(Material.OAK_SAPLING, TreeType.TREE), Climate.warmArid, Season.autumn )
	case Beetroot extends Plant( PlantType.ageable(Material.BEETROOTS), Climate.warmArid, Season.autumn )

	case Potato extends Plant( PlantType.ageable(Material.POTATOES), Climate.coldHumid, Season.spring )
	case SugarCane extends Plant( PlantType.verticalPlant(Material.SUGAR_CANE), Climate.coldHumid, Season.spring )
	case Plum extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.plum), Climate.coldHumid, Season.summer )
	case DarkOak extends Plant( PlantType.generateTree(Material.DARK_OAK_SAPLING, TreeType.DARK_OAK), Climate.coldHumid, Season.summer )
	case Spruce extends Plant( PlantType.generateTree(Material.SPRUCE_SAPLING, TreeType.REDWOOD), Climate.coldHumid, Season.autumn )
	case Apple extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, ItemStack(Material.APPLE)), Climate.coldHumid, Season.autumn )

	case Apricot extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.apricot), Climate.coldArid, Season.spring )
	case Birch extends Plant( PlantType.generateTree(Material.BIRCH_SAPLING, TreeType.BIRCH), Climate.coldArid, Season.spring )
	case SweetBerry extends Plant( PlantType.ageable(Material.SWEET_BERRY_BUSH), Climate.coldArid, Season.summer )
	case Pear extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.pear), Climate.coldArid, Season.summer )
	case PumpkinStem extends Plant( PlantType.stemmedAgeable(Material.PUMPKIN_STEM, Material.PUMPKIN), Climate.coldArid, Season.autumn )
	case Wheat extends Plant( PlantType.ageable(Material.WHEAT), Climate.coldArid, Season.autumn )