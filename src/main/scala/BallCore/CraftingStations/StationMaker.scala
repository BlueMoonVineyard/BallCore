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
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry
import BallCore.Alloys.Tier1

object StationMaker:
    val recipes: List[Recipe] = List(
        Recipe(
            trans"recipes.make-dye-vat",
            NamespacedKey("ballcore", "make_dye_vat"),
            List(
                (Vanilla(Material.CAULDRON), 1),
                (Vanilla(Material.CYAN_DYE), 32),
                (Vanilla(Material.YELLOW_DYE), 32),
                (Vanilla(Material.MAGENTA_DYE), 32),
                (Vanilla(Material.BLACK_DYE), 16),
            ),
            List(
                (DyeVat.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-glazing-kiln",
            NamespacedKey("ballcore", "make_glazing_kiln"),
            List(
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.CYAN_DYE), 32),
                (Vanilla(Material.YELLOW_DYE), 32),
                (Vanilla(Material.MAGENTA_DYE), 32),
                (Vanilla(Material.BLACK_DYE), 16),
            ),
            List(
                (GlazingKiln.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-kiln",
            NamespacedKey("ballcore", "make_kiln"),
            List(
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.SAND, Material.RED_SAND), 32),
                (Vanilla(Material.GRAVEL), 32),
                (Vanilla(Material.CLAY), 32),
            ),
            List(
                (Kiln.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-woodcutter",
            NamespacedKey("ballcore", "make_woodcutter"),
            List(
                (Vanilla(Material.STONECUTTER), 1),
                (Vanilla(Material.CHEST), 8),
                (Vanilla(Material.STONE_AXE), 1),
            ),
            List(
                (Woodcutter.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-concrete-mixer",
            NamespacedKey("ballcore", "make_concrete_mixer"),
            List(
                (Vanilla(Material.DECORATED_POT), 1),
                (Vanilla(Material.SAND), 64),
                (Vanilla(Material.GRAVEL), 64),
            ),
            List(
                (ConcreteMixer.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-rail-manufactory",
            NamespacedKey("ballcore", "make_rail_manufactory"),
            List(
                (Vanilla(Material.PISTON), 1),
                (Vanilla(Material.RAIL), 32),
                (Vanilla(Material.REDSTONE), 16),
            ),
            List(
                (RailManufactory.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-redstone-maker",
            NamespacedKey("ballcore", "make_redstone_maker"),
            List(
                (Vanilla(Material.PISTON), 1),
                (Vanilla(Material.REDSTONE), 64),
            ),
            List(
                (RedstoneMaker.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-carnivore-kitchen",
            NamespacedKey("ballcore", "make_carnivore_kitchen"),
            List(
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.BEEF), 16),
                (Vanilla(Material.CHICKEN), 16),
                (Vanilla(Material.PORKCHOP), 16),
                (Vanilla(Material.MUTTON), 16),
            ),
            List(
                (CarnivoreKitchen.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-herbivore-kitchen",
            NamespacedKey("ballcore", "make_herbivore_kitchen"),
            List(
                (Vanilla(Material.SMOKER), 1),
                (Vanilla(Material.BREAD), 64),
            ),
            List(
                (HerbivoreKitchen.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-slimer",
            NamespacedKey("ballcore", "make_slimer"),
            List(
                (Vanilla(Material.CRAFTING_TABLE), 1),
                (Vanilla(Material.SLIME_BALL), 1),
            ),
            List(
                (Slimer.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-economist",
            NamespacedKey("ballcore", "make_economist"),
            List(
                (Vanilla(Material.CARTOGRAPHY_TABLE), 1),
                (Vanilla(Material.CHEST), 4),
                (Vanilla(Material.PAPER), 4),
            ),
            List(
                (Economist.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-ice-box",
            NamespacedKey("ballcore", "make_ice_box"),
            List(
                (Vanilla(Material.CAULDRON), 1),
                (Vanilla(Material.ICE), 256),
                (Vanilla(Material.PACKED_ICE), 32),
                (Vanilla(Material.BLUE_ICE), 16),
            ),
            List(
                (IceBox.template, 1)
            ),
            30,
            1,
        ),
        Recipe(
            trans"recipes.make-bundle-stuffer",
            NamespacedKey("ballcore", "make_bundle_stuffer"),
            List(
                (Vanilla(Material.FLETCHING_TABLE), 1),
                (Custom(Tier1.all.map(_.stack): _*), 64),
                (Vanilla(Material.BUNDLE), 1),
            ),
            List(
                (BundleStuffer.template, 1)
            ),
            30,
            2,
        ),
    )
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "station_maker"),
        Material.CARTOGRAPHY_TABLE,
        trans"items.station-maker",
        trans"items.station-maker.lore",
    )

class StationMaker()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(StationMaker.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = StationMaker.template
