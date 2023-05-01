package BallCore.CraftingStations

import org.bukkit.Material
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.ItemStack
import BallCore.UI.Prompts
import org.bukkit.plugin.Plugin
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey

object Kiln:
	val pairs = List(
		(Material.COBBLESTONE, Material.STONE, (3, 2), "Smelt Stone"),
		(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, (3, 2), "Smelt Deepslate"),
		(Material.STONE, Material.SMOOTH_STONE, (1, 1), "Smelt Smooth Stone"),

		(Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, (1, 1), "Crack Stone Bricks"),
		(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS, (1, 1), "Crack Deepslate Bricks"),

		(Material.CLAY_BALL, Material.BRICK, (2, 1), "Fire Bricks"),
		(Material.CLAY, Material.TERRACOTTA, (2, 1), "Fire Terracotta"),

		(Material.SAND, Material.SANDSTONE, (1, 1), "Smelt Sandstone"),
		(Material.SANDSTONE, Material.SMOOTH_SANDSTONE, (1, 1), "Smelt Smooth Sandstone"),

		(Material.RED_SAND, Material.RED_SANDSTONE, (1, 1), "Smelt Red Sandstone"),
		(Material.RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE, (1, 1), "Smelt Red Smooth Sandstone"),
	)
	val recipes = pairs.map { (in, out, ratio, name) =>
		val (mult, div) = ratio
		Recipe(name, List((MaterialChoice(in), 64)), List(ItemStack(out, (64 * mult) / div)), 10)
	}.appended {
		Recipe("Smelt Glass", List((MaterialChoice(Material.SAND, Material.RED_SAND, Material.SOUL_SAND), 64)), List(ItemStack(Material.GLASS, 64)), 10)
	}

class Kiln()(using act: CraftingActor, p: Plugin, prompts: Prompts) extends CraftingStation(Kiln.recipes):
    def group = CraftingStations.group
    def template = CustomItemStack.make(NamespacedKey("ballcore", "kiln"), Material.CAULDRON, "&rKiln", "&rSmelts nonmetals more efficiently than normal smelting")
