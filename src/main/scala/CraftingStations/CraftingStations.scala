package BallCore.CraftingStations

import org.bukkit.plugin.Plugin
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.UI.Prompts

object CraftingStations:
	val group = ItemGroup(NamespacedKey("ballcore", "crafting_stations"), ItemStack(Material.CRAFTING_TABLE))
	def register()(using p: Plugin, registry: ItemRegistry, prompts: Prompts): Unit =
		given act: CraftingActor = CraftingActor()
		Thread.startVirtualThread(() => act.mainLoop())
		registry.register(DyeVat())
