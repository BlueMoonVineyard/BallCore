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

object RailManufactory:
    val pairs: List[(List[(RecipeIngredient, Int)], Material, (Int, Int))] =
        List(
            (
                List(Vanilla(Material.IRON_INGOT) -> 64, Vanilla(Material.STICK) -> 8),
                Material.RAIL,
                (256, 342),
            ),
            (
                List(
                    Vanilla(Material.GOLD_INGOT) -> 64,
                    Vanilla(Material.STICK) -> 8,
                    Vanilla(Material.REDSTONE) -> 8,
                ),
                Material.POWERED_RAIL,
                (256, 342),
            ),
            (
                List(
                    Vanilla(Material.IRON_INGOT) -> 32,
                    Vanilla(Material.STONE_PRESSURE_PLATE) -> 8,
                    Vanilla(Material.REDSTONE) -> 8,
                ),
                Material.DETECTOR_RAIL,
                (8, 16),
            ),
            (
                List(Vanilla(Material.IRON_INGOT) -> 64),
                Material.MINECART,
                (16, 24),
            ),
        )
    val recipes: List[Recipe] = pairs.flatMap {
        (ingredients, output, outputCounts) =>
            val (lo, hi) = outputCounts
            val key = output.getKey().toString().replace(':', '_')
            List(
                Recipe(
                    txt"Make ${output.asComponent} (low people & efficiency)",
                    NamespacedKey("ballcore", s"make_${key}_low"),
                    ingredients,
                    List((ItemStack(output), lo)),
                    10,
                    1,
                ),
                Recipe(
                    txt"Make ${output.asComponent} (high people & efficiency)",
                    NamespacedKey("ballcore", s"make_${key}_high"),
                    ingredients,
                    List((ItemStack(output), hi)),
                    15,
                    2,
                ),
            )
    }
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "rail_manufactory"),
        Material.PISTON,
        txt"Rail Manufactory",
        txt"Crafts metals into rails at bulk rates",
    )

class RailManufactory()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(RailManufactory.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = RailManufactory.template
