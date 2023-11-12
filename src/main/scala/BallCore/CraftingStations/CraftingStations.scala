// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItem, ItemGroup, ItemRegistry, Listeners}
import BallCore.DataStructures.ShutdownCallbacks
import BallCore.UI.{Prompts, UIProgramRunner}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.{ItemStack, ShapedRecipe}
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}

object CraftingStations:
    val group: ItemGroup = ItemGroup(
        NamespacedKey("ballcore", "crafting_stations"),
        ItemStack(Material.CRAFTING_TABLE),
    )

    def register()(using
        p: Plugin,
        registry: ItemRegistry,
        prompts: Prompts,
        sm: ShutdownCallbacks,
    ): Unit =
        given act: CraftingActor = CraftingActor()

        act.startListener()
        registry.register(DyeVat())
        registry.register(GlazingKiln())
        registry.register(Kiln())
        registry.register(StationMaker())
        registry.register(ConcreteMixer())
        registry.register(RailManufactory())
        registry.register(Woodcutter())
        registry.register(RedstoneMaker())
        val smRecipe = ShapedRecipe(
            NamespacedKey("ballcore", "craft_station_maker"),
            StationMaker.template,
        )
        smRecipe.shape(
            "II",
            "II",
        )
        smRecipe.setIngredient('I', MaterialChoice(Material.CRAFTING_TABLE))
        registry.addRecipe(smRecipe)

abstract class CraftingStation(recipes: List[Recipe])(using
    act: CraftingActor,
    p: Plugin,
    prompts: Prompts,
) extends CustomItem,
      Listeners.BlockClicked:
    def onBlockClicked(event: PlayerInteractEvent): Unit =
        val p = RecipeSelectorProgram(recipes)
        val plr = event.getPlayer
        val runner =
            UIProgramRunner(p, p.Flags(plr, event.getClickedBlock), plr)
        runner.render()
