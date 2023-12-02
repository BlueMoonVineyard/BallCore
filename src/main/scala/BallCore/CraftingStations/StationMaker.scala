// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.UI.Elements.*
import BallCore.UI.Prompts
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}
import RecipeIngredient.*

object StationMaker:
    val recipes: List[Recipe] = List(
        Recipe(
            "Make Dye Vat",
            List(
                (Vanilla(Material.CAULDRON), 1),
                (Vanilla(Material.CYAN_DYE), 32),
                (Vanilla(Material.YELLOW_DYE), 32),
                (Vanilla(Material.MAGENTA_DYE), 32),
                (Vanilla(Material.BLACK_DYE), 16),
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
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.CYAN_DYE), 32),
                (Vanilla(Material.YELLOW_DYE), 32),
                (Vanilla(Material.MAGENTA_DYE), 32),
                (Vanilla(Material.BLACK_DYE), 16),
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
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.SAND, Material.RED_SAND), 32),
                (Vanilla(Material.GRAVEL), 32),
                (Vanilla(Material.CLAY), 32),
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
                (Vanilla(Material.STONECUTTER), 1),
                (Vanilla(Material.CHEST), 8),
                (Vanilla(Material.STONE_AXE), 1),
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
                (Vanilla(Material.DECORATED_POT), 1),
                (Vanilla(Material.SAND), 64),
                (Vanilla(Material.GRAVEL), 64),
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
                (Vanilla(Material.PISTON), 1),
                (Vanilla(Material.RAIL), 32),
                (Vanilla(Material.REDSTONE), 16),
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
                (Vanilla(Material.PISTON), 1),
                (Vanilla(Material.REDSTONE), 64),
                (Vanilla(Material.QUARTZ), 32),
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
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.BEEF), 16),
                (Vanilla(Material.CHICKEN), 16),
                (Vanilla(Material.PORKCHOP), 16),
                (Vanilla(Material.MUTTON), 16),
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
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.BREAD), 64),
            ),
            List(
                HerbivoreKitchen.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Alloyer",
            List(
                (Vanilla(Material.BLAST_FURNACE), 1),
                (Vanilla(Material.STONE), 64),
                (Vanilla(Material.LAVA_BUCKET), 1),
            ),
            List(
                Tier1Alloyer.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Slimer",
            List(
                (Vanilla(Material.CRAFTING_TABLE), 1),
                (Vanilla(Material.SLIME_BALL), 1),
            ),
            List(
                Slimer.template
            ),
            30,
            1,
        ),
        Recipe(
            "Make Economist",
            List(
                (Vanilla(Material.CARTOGRAPHY_TABLE), 1),
                (Vanilla(Material.CHEST), 4),
                (Vanilla(Material.PAPER), 4),
            ),
            List(
                Economist.template
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
