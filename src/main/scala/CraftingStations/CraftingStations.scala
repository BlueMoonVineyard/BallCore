package BallCore.CraftingStations

import org.bukkit.plugin.Plugin
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.UI.Prompts
import org.bukkit.event.player.PlayerInteractEvent
import BallCore.CustomItems.Listeners
import BallCore.CustomItems.CustomItem
import BallCore.UI.UIProgramRunner
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import BallCore.DataStructures.ShutdownCallbacks

object CraftingStations:
	val group = ItemGroup(NamespacedKey("ballcore", "crafting_stations"), ItemStack(Material.CRAFTING_TABLE))
	def register()(using p: Plugin, registry: ItemRegistry, prompts: Prompts, sm: ShutdownCallbacks): Unit =
		given act: CraftingActor = CraftingActor()
		act.startListener()
		registry.register(DyeVat())
		registry.register(GlazingKiln())
		registry.register(Kiln())
		registry.register(StationMaker())
		val smRecipe = ShapedRecipe(NamespacedKey("ballcore", "craft_station_maker"), StationMaker.template)
		smRecipe.shape(
			"II",
			"II",
		)
		smRecipe.setIngredient('I', MaterialChoice(Material.CRAFTING_TABLE))
		p.getServer().addRecipe(smRecipe)

abstract class CraftingStation(recipes: List[Recipe])(using act: CraftingActor, p: Plugin, prompts: Prompts) extends CustomItem, Listeners.BlockClicked:
	def onBlockClicked(event: PlayerInteractEvent): Unit =
		val p = RecipeSelectorProgram(recipes)
		val plr = event.getPlayer()
		val runner = UIProgramRunner(p, p.Flags(plr, event.getClickedBlock()), plr)
		runner.render()
