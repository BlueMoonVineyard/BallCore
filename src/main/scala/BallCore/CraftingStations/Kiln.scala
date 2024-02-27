// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.UI.Elements.*
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}
import RecipeIngredient.*
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry
import net.kyori.adventure.text.Component

object Kiln:
    val pairs
        : List[(Material, Material, (Int, Int), Component, NamespacedKey)] =
        List(
            (
                Material.COBBLESTONE,
                Material.STONE,
                (3, 2),
                trans"recipes.smelt-stone",
                NamespacedKey("ballcore", "smelt_stone"),
            ),
            (
                Material.COBBLED_DEEPSLATE,
                Material.DEEPSLATE,
                (3, 2),
                trans"recipes.smelt-deepslate",
                NamespacedKey("ballcore", "smelt_deepslate"),
            ),
            (
                Material.STONE,
                Material.SMOOTH_STONE,
                (1, 1),
                trans"recipes.smelt-smooth-stone",
                NamespacedKey("ballcore", "smelt_smooth_stone"),
            ),
            (
                Material.STONE_BRICKS,
                Material.CRACKED_STONE_BRICKS,
                (1, 1),
                trans"recipes.crack-stone-bricks",
                NamespacedKey("ballcore", "crack_stone_bricks"),
            ),
            (
                Material.DEEPSLATE_BRICKS,
                Material.DEEPSLATE_BRICKS,
                (1, 1),
                trans"recipes.crack-deepslate-bricks",
                NamespacedKey("ballcore", "crack_deepslate_bricks"),
            ),
            (
                Material.CLAY_BALL,
                Material.BRICK,
                (2, 1),
                trans"recipes.fire-bricks",
                NamespacedKey("ballcore", "fire_bricks"),
            ),
            (
                Material.CLAY,
                Material.TERRACOTTA,
                (2, 1),
                trans"recipes.fire-terracotta",
                NamespacedKey("ballcore", "fire_terracotta"),
            ),
            (
                Material.SAND,
                Material.SANDSTONE,
                (1, 1),
                trans"recipes.smelt-sandstone",
                NamespacedKey("ballcore", "smelt_sandstone"),
            ),
            (
                Material.SANDSTONE,
                Material.SMOOTH_SANDSTONE,
                (1, 1),
                trans"recipes.smelt-smooth-sandstone",
                NamespacedKey("ballcore", "smelt_smooth_sandstone"),
            ),
            (
                Material.RED_SAND,
                Material.RED_SANDSTONE,
                (1, 1),
                trans"recipes.smelt-red-sandstone",
                NamespacedKey("ballcore", "smelt_red_sandstone"),
            ),
            (
                Material.RED_SANDSTONE,
                Material.SMOOTH_RED_SANDSTONE,
                (1, 1),
                trans"recipes.smelt-red-smooth-sandstone",
                NamespacedKey("ballcore", "smelt_smooth_red_sandstone"),
            ),
        )
    val recipes: List[Recipe] = pairs
        .map { (in, out, ratio, name, id) =>
            val (mult, div) = ratio
            Recipe(
                name,
                id,
                List((Vanilla(in), 64)),
                List((ItemStack(out), (64 * mult) / div)),
                10,
                1,
            )
        }
        .appended {
            Recipe(
                trans"recipes.smelt-glass",
                NamespacedKey("ballcore", "smelt_glass"),
                List(
                    (
                        Vanilla(
                            Material.SAND,
                            Material.RED_SAND,
                            Material.SOUL_SAND,
                        ),
                        64,
                    )
                ),
                List((ItemStack(Material.GLASS), 64)),
                10,
                1,
            )
        }
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "kiln"),
        Material.SMOKER,
        trans"items.kiln",
        trans"items.kiln.lore",
    )

class Kiln()(using CraftingActor, Plugin, Prompts, SQLManager, ItemRegistry)
    extends CraftingStation(Kiln.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = Kiln.template
