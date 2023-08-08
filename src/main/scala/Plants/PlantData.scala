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
import BallCore.UI.Elements._

object Fruit:
	val fruits = ItemGroup(NamespacedKey("ballcore", "fruits"), ItemStack(Material.APPLE))
	val apricot = CustomItemStack.make(NamespacedKey("ballcore", "apricot"), Material.APPLE, txt"Apricot")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(1))))
	val peach = CustomItemStack.make(NamespacedKey("ballcore", "peach"), Material.APPLE, txt"Peach")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(2))))
	val pear = CustomItemStack.make(NamespacedKey("ballcore", "pear"), Material.APPLE, txt"Pear")
		.tap(is => is.setItemMeta(is.getItemMeta().tap(_.setCustomModelData(3))))
	val plum = CustomItemStack.make(NamespacedKey("ballcore", "plum"), Material.APPLE, txt"Plum")
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
	case ageable(seed: Material, hoursBetweenStages: Int)
	case generateTree(sapling: Material, kind: TreeType, growthTime: Int)
	case stemmedAgeable(stem: Material, fruit: Material, hoursBetweenStages: Int)
	// sugarcane and cacti
	case verticalPlant(what: Material, hoursBetweenStages: Int)
	case bamboo(hoursBetweenStages: Int)
	case fruitTree(looksLike: TreeType, fruit: ItemStack, growthTime: Int)

	def hours(): Int =
		this match
			case ageable(seed, hoursBetweenStages) => hoursBetweenStages
			case generateTree(sapling, kind, growthTime) => growthTime
			case stemmedAgeable(stem, fruit, hoursBetweenStages) => hoursBetweenStages
			case verticalPlant(what, hoursBetweenStages) => hoursBetweenStages
			case bamboo(hoursBetweenStages) => hoursBetweenStages
			case fruitTree(looksLike, fruit, growthTime) => growthTime

enum Plant(
	val plant: PlantType,
	val growingClimate: Climate,
	val growingSeason: Season,
):
	case Mangrove extends Plant( PlantType.generateTree(Material.MANGROVE_PROPAGULE, TreeType.MANGROVE, 24 * 7), Climate.warmHumid, Season.spring )
	case Jungle extends Plant( PlantType.generateTree(Material.JUNGLE_SAPLING, TreeType.JUNGLE, 24 * 7), Climate.warmHumid, Season.spring )
	case Bamboo extends Plant( PlantType.bamboo(4), Climate.warmHumid, Season.summer )
	case Azalea extends Plant( PlantType.generateTree(Material.AZALEA, TreeType.AZALEA, 24 * 7), Climate.warmHumid, Season.summer )
	case Acacia extends Plant( PlantType.generateTree(Material.ACACIA_SAPLING, TreeType.ACACIA, 24 * 7), Climate.warmHumid, Season.autumn )
	case Cocoa extends Plant( PlantType.ageable(Material.COCOA, 12), Climate.warmHumid, Season.autumn )

	case Carrot extends Plant( PlantType.ageable(Material.CARROTS, 6), Climate.warmArid, Season.spring )
	case Peach extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.peach, 24 * 14), Climate.warmArid, Season.spring )
	case Cactus extends Plant( PlantType.verticalPlant(Material.CACTUS, 12), Climate.warmArid, Season.summer )
	case Melon extends Plant( PlantType.stemmedAgeable(Material.MELON_STEM, Material.MELON, 6), Climate.warmArid, Season.summer )
	case Oak extends Plant( PlantType.generateTree(Material.OAK_SAPLING, TreeType.TREE, 24 * 7), Climate.warmArid, Season.autumn )
	case Beetroot extends Plant( PlantType.ageable(Material.BEETROOTS, 5), Climate.warmArid, Season.autumn )

	case Potato extends Plant( PlantType.ageable(Material.POTATOES, 7), Climate.coldHumid, Season.spring )
	case SugarCane extends Plant( PlantType.verticalPlant(Material.SUGAR_CANE, 12), Climate.coldHumid, Season.spring )
	case Plum extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.plum, 24 * 14), Climate.coldHumid, Season.summer )
	case DarkOak extends Plant( PlantType.generateTree(Material.DARK_OAK_SAPLING, TreeType.DARK_OAK, 24 * 7), Climate.coldHumid, Season.summer )
	case Spruce extends Plant( PlantType.generateTree(Material.SPRUCE_SAPLING, TreeType.REDWOOD, 24 * 7), Climate.coldHumid, Season.autumn )
	case Apple extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, ItemStack(Material.APPLE), 24 * 14), Climate.coldHumid, Season.autumn )

	case Apricot extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.apricot, 24 * 14), Climate.coldArid, Season.spring )
	case Birch extends Plant( PlantType.generateTree(Material.BIRCH_SAPLING, TreeType.BIRCH, 24 * 7), Climate.coldArid, Season.spring )
	case SweetBerry extends Plant( PlantType.ageable(Material.SWEET_BERRY_BUSH, 3), Climate.coldArid, Season.summer )
	case Pear extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.pear, 24 * 14), Climate.coldArid, Season.summer )
	case PumpkinStem extends Plant( PlantType.stemmedAgeable(Material.PUMPKIN_STEM, Material.PUMPKIN, 6), Climate.coldArid, Season.autumn )
	case Wheat extends Plant( PlantType.ageable(Material.WHEAT, 6), Climate.coldArid, Season.autumn )
