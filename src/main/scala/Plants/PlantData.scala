// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

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
import BallCore.Acclimation.Information
import net.kyori.adventure.text.Component

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

	def display: String =
		this match
			case Climate.warmArid => "warm, arid"
			case Climate.warmHumid => "warm, humid"
			case Climate.coldArid => "cold, arid"
			case Climate.coldHumid => "cold, humid"

object Climate:
	def climateAt(x: Int, y: Int, z: Int): Climate =
		val isWet = Information.humidity(x, y, z) >= 0.55
		val isWarm = Information.temperature(x, y, z) >= 0.6
		(isWet, isWarm) match
			case (false, false) => Climate.coldArid
			case (false, true) => Climate.warmArid
			case (true, false) => Climate.coldHumid
			case (true, true) => Climate.warmHumid

// divided by how we're supposed to grow the plant
enum PlantType:
	case ageable(seed: Material, hoursBetweenStages: Int)
	case generateTree(sapling: Material, kind: TreeType, growthTime: Int)
	case stemmedAgeable(stem: Material, fruit: Material, hoursBetweenStages: Int)
	// sugarcane and cacti
	case verticalPlant(what: Material, hoursBetweenStages: Int)
	case bamboo(hoursBetweenStages: Int)
	case fruitTree(looksLike: TreeType, fruit: ItemStack, growthTime: Int)

	def representativeItem(): ItemStack =
		this match
			case ageable(seed, _) if seed == Material.COCOA =>
				ItemStack(Material.COCOA_BEANS)
			case ageable(seed, _) if seed == Material.CARROTS =>
				ItemStack(Material.CARROT)
			case ageable(seed, _) if seed == Material.BEETROOTS =>
				ItemStack(Material.BEETROOT)
			case ageable(seed, _) if seed == Material.POTATOES =>
				ItemStack(Material.POTATO)
			case ageable(seed, _) if seed == Material.SWEET_BERRY_BUSH =>
				ItemStack(Material.SWEET_BERRIES)
			case ageable(seed, _) => ItemStack(seed)
			case generateTree(sapling, _, _) => ItemStack(sapling)
			case stemmedAgeable(_, fruit, _) => ItemStack(fruit)
			case verticalPlant(what, _) => ItemStack(what)
			case bamboo(_) => ItemStack(Material.BAMBOO)
			case fruitTree(_, fruit, _) => fruit.clone()
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
	val name: Component,
	val growingClimate: Climate,
	val growingSeason: Season,
):
	case Mangrove extends Plant( PlantType.generateTree(Material.MANGROVE_PROPAGULE, TreeType.MANGROVE, 24 * 7), Component.translatable(Material.MANGROVE_PROPAGULE), Climate.warmHumid, Season.spring )
	case Jungle extends Plant( PlantType.generateTree(Material.JUNGLE_SAPLING, TreeType.JUNGLE, 24 * 7), Component.translatable(Material.JUNGLE_SAPLING), Climate.warmHumid, Season.spring )
	case Bamboo extends Plant( PlantType.bamboo(4), Component.translatable(Material.BAMBOO), Climate.warmHumid, Season.summer )
	case Azalea extends Plant( PlantType.generateTree(Material.AZALEA, TreeType.AZALEA, 24 * 7), Component.translatable(Material.AZALEA), Climate.warmHumid, Season.summer )
	case Acacia extends Plant( PlantType.generateTree(Material.ACACIA_SAPLING, TreeType.ACACIA, 24 * 7), Component.translatable(Material.ACACIA_SAPLING), Climate.warmHumid, Season.autumn )
	case Cocoa extends Plant( PlantType.ageable(Material.COCOA, 12), Component.translatable(Material.COCOA), Climate.warmHumid, Season.autumn )

	case Carrot extends Plant( PlantType.ageable(Material.CARROTS, 6), Component.translatable(Material.CARROTS), Climate.warmArid, Season.spring )
	case Peach extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.peach, 24 * 14), Component.text("Bamboo"), Climate.warmArid, Season.spring )
	case Cactus extends Plant( PlantType.verticalPlant(Material.CACTUS, 12), Component.translatable(Material.CACTUS), Climate.warmArid, Season.summer )
	case Melon extends Plant( PlantType.stemmedAgeable(Material.MELON_STEM, Material.MELON, 6), Component.translatable(Material.MELON_STEM), Climate.warmArid, Season.summer )
	case Oak extends Plant( PlantType.generateTree(Material.OAK_SAPLING, TreeType.TREE, 24 * 7), Component.translatable(Material.OAK_SAPLING), Climate.warmArid, Season.autumn )
	case Beetroot extends Plant( PlantType.ageable(Material.BEETROOTS, 5), Component.translatable(Material.BEETROOTS), Climate.warmArid, Season.autumn )

	case Potato extends Plant( PlantType.ageable(Material.POTATOES, 7), Component.translatable(Material.POTATOES), Climate.coldHumid, Season.spring )
	case SugarCane extends Plant( PlantType.verticalPlant(Material.SUGAR_CANE, 12), Component.translatable(Material.SUGAR_CANE), Climate.coldHumid, Season.spring )
	case Plum extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.plum, 24 * 14), Component.text("Plum"), Climate.coldHumid, Season.summer )
	case DarkOak extends Plant( PlantType.generateTree(Material.DARK_OAK_SAPLING, TreeType.DARK_OAK, 24 * 7), Component.translatable(Material.DARK_OAK_SAPLING), Climate.coldHumid, Season.summer )
	case Spruce extends Plant( PlantType.generateTree(Material.SPRUCE_SAPLING, TreeType.REDWOOD, 24 * 7), Component.translatable(Material.SPRUCE_SAPLING), Climate.coldHumid, Season.autumn )
	case Apple extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, ItemStack(Material.APPLE), 24 * 14), Component.translatable(Material.APPLE), Climate.coldHumid, Season.autumn )

	case Apricot extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.apricot, 24 * 14), Component.text("Apricot"), Climate.coldArid, Season.spring )
	case Birch extends Plant( PlantType.generateTree(Material.BIRCH_SAPLING, TreeType.BIRCH, 24 * 7), Component.translatable(Material.BIRCH_SAPLING), Climate.coldArid, Season.spring )
	case SweetBerry extends Plant( PlantType.ageable(Material.SWEET_BERRY_BUSH, 3), Component.translatable(Material.SWEET_BERRY_BUSH), Climate.coldArid, Season.summer )
	case Pear extends Plant( PlantType.fruitTree(TreeType.BIG_TREE, Fruit.pear, 24 * 14), Component.text("Pear"), Climate.coldArid, Season.summer )
	case PumpkinStem extends Plant( PlantType.stemmedAgeable(Material.PUMPKIN_STEM, Material.PUMPKIN, 6), Component.translatable(Material.PUMPKIN_STEM), Climate.coldArid, Season.autumn )
	case Wheat extends Plant( PlantType.ageable(Material.WHEAT, 6), Component.translatable(Material.WHEAT), Climate.coldArid, Season.autumn )
