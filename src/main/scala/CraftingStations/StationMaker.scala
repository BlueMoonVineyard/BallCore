package BallCore.CraftingStations

import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.Material
import org.bukkit.plugin.Plugin
import BallCore.UI.Prompts
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import BallCore.UI.Elements._

object StationMaker:
	val recipes = List(
		Recipe(
			"Make Dye Vat",
			List(
				(MaterialChoice(Material.CAULDRON), 1),
				(MaterialChoice(Material.CYAN_DYE), 32),
				(MaterialChoice(Material.YELLOW_DYE), 32),
				(MaterialChoice(Material.MAGENTA_DYE), 32),
				(MaterialChoice(Material.BLACK_DYE), 16),
			),
			List(
				DyeVat.template,
			),
			30,
			1,
		),
		Recipe(
			"Make Glazing Kiln",
			List(
				(MaterialChoice(Material.SMOKER), 1),
				(MaterialChoice(Material.CYAN_DYE), 32),
				(MaterialChoice(Material.YELLOW_DYE), 32),
				(MaterialChoice(Material.MAGENTA_DYE), 32),
				(MaterialChoice(Material.BLACK_DYE), 16),
			),
			List(
				GlazingKiln.template,
			),
			30,
			1,
		),
		Recipe(
			"Make Kiln",
			List(
				(MaterialChoice(Material.SMOKER), 1),
				(MaterialChoice(Material.SAND, Material.RED_SAND), 32),
				(MaterialChoice(Material.GRAVEL), 32),
				(MaterialChoice(Material.CLAY), 32),
			),
			List(
				Kiln.template,
			),
			30,
			1,
		),
	)
	val template = CustomItemStack.make(NamespacedKey("ballcore", "station_maker"), Material.CARTOGRAPHY_TABLE, txt"Station Maker", txt"Allows creating improved crafting stations")

class StationMaker()(using act: CraftingActor, p: Plugin, prompts: Prompts) extends CraftingStation(StationMaker.recipes):
    def group = CraftingStations.group
    def template = StationMaker.template
