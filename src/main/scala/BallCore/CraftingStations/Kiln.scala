// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.UI.Elements.*
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}

object Kiln:
    val pairs: List[(Material, Material, (Int, Int), String)] = List(
        (Material.COBBLESTONE, Material.STONE, (3, 2), "Smelt Stone"),
        (
            Material.COBBLED_DEEPSLATE,
            Material.DEEPSLATE,
            (3, 2),
            "Smelt Deepslate",
        ),
        (Material.STONE, Material.SMOOTH_STONE, (1, 1), "Smelt Smooth Stone"),
        (
            Material.STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS,
            (1, 1),
            "Crack Stone Bricks",
        ),
        (
            Material.DEEPSLATE_BRICKS,
            Material.DEEPSLATE_BRICKS,
            (1, 1),
            "Crack Deepslate Bricks",
        ),
        (Material.CLAY_BALL, Material.BRICK, (2, 1), "Fire Bricks"),
        (Material.CLAY, Material.TERRACOTTA, (2, 1), "Fire Terracotta"),
        (Material.SAND, Material.SANDSTONE, (1, 1), "Smelt Sandstone"),
        (
            Material.SANDSTONE,
            Material.SMOOTH_SANDSTONE,
            (1, 1),
            "Smelt Smooth Sandstone",
        ),
        (
            Material.RED_SAND,
            Material.RED_SANDSTONE,
            (1, 1),
            "Smelt Red Sandstone",
        ),
        (
            Material.RED_SANDSTONE,
            Material.SMOOTH_RED_SANDSTONE,
            (1, 1),
            "Smelt Red Smooth Sandstone",
        ),
    )
    val recipes: List[Recipe] = pairs
        .map { (in, out, ratio, name) =>
            val (mult, div) = ratio
            Recipe(
                name,
                List((MaterialChoice(in), 64)),
                List(ItemStack(out, (64 * mult) / div)),
                10,
                1,
            )
        }
        .appended {
            Recipe(
                "Smelt Glass",
                List(
                    (
                        MaterialChoice(
                            Material.SAND,
                            Material.RED_SAND,
                            Material.SOUL_SAND,
                        ),
                        64,
                    )
                ),
                List(ItemStack(Material.GLASS, 64)),
                10,
                1,
            )
        }
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "kiln"),
        Material.SMOKER,
        txt"Kiln",
        txt"Smelts nonmetals more efficiently than normal smelting",
    )

class Kiln()(using act: CraftingActor, p: Plugin, prompts: Prompts)
    extends CraftingStation(Kiln.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = Kiln.template
