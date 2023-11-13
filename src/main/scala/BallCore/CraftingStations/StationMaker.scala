// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.UI.Elements.*
import BallCore.UI.Prompts
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}

object StationMaker:
    val recipes: List[Recipe] = List(
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
                DyeVat.template
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
                GlazingKiln.template
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
                Kiln.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Woodcutter",
            List(
                (MaterialChoice(Material.STONECUTTER), 1),
                (MaterialChoice(Material.CHEST), 8),
                (MaterialChoice(Material.STONE_AXE), 1),
            ),
            List(
                Woodcutter.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Concrete Mixer",
            List(
                (MaterialChoice(Material.DECORATED_POT), 1),
                (MaterialChoice(Material.SAND), 64),
                (MaterialChoice(Material.GRAVEL), 64),
            ),
            List(
                ConcreteMixer.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Rail Manufactory",
            List(
                (MaterialChoice(Material.PISTON), 1),
                (MaterialChoice(Material.RAIL), 32),
                (MaterialChoice(Material.REDSTONE), 16),
            ),
            List(
                RailManufactory.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Redstone Maker",
            List(
                (MaterialChoice(Material.PISTON), 1),
                (MaterialChoice(Material.REDSTONE), 64),
                (MaterialChoice(Material.QUARTZ), 32),
            ),
            List(
                RedstoneMaker.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Carnivore Kitchen",
            List(
                (MaterialChoice(Material.SMOKER), 1),
                (MaterialChoice(Material.BEEF), 16),
                (MaterialChoice(Material.CHICKEN), 16),
                (MaterialChoice(Material.PORKCHOP), 16),
                (MaterialChoice(Material.MUTTON), 16),
            ),
            List(
                CarnivoreKitchen.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Herbivore Kitchen",
            List(
                (MaterialChoice(Material.SMOKER), 1),
                (MaterialChoice(Material.BREAD), 64),
            ),
            List(
                HerbivoreKitchen.template
            ),
            30,
            1,
        ),
    )
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "station_maker"),
        Material.CARTOGRAPHY_TABLE,
        txt"Station Maker",
        txt"Allows creating improved crafting stations",
    )

class StationMaker()(using act: CraftingActor, p: Plugin, prompts: Prompts)
    extends CraftingStation(StationMaker.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = StationMaker.template
