// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import org.bukkit.Material
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.ItemStack
import BallCore.UI.Prompts
import org.bukkit.plugin.Plugin
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import BallCore.UI.Elements._
import BallCore.Ores.QuadrantOres.ItemStacks.ironLikes
import org.bukkit.inventory.RecipeChoice.ExactChoice
import scala.jdk.CollectionConverters._
import BallCore.Ores.QuadrantOres.ItemStacks.goldLikes

object RailManufactory:
    val ironChoice = ExactChoice(ironLikes.map(_.ingot).asJava)
    val goldChoice = ExactChoice(goldLikes.map(_.ingot).asJava)
    val pairs = List(
        (List(ironChoice -> 64, MaterialChoice(Material.STICK) -> 8), Material.RAIL, (256, 342), "Make Rail"),
        (List(goldChoice -> 64, MaterialChoice(Material.STICK) -> 8, MaterialChoice(Material.REDSTONE) -> 8), Material.POWERED_RAIL, (256, 342), "Make Powered Rail"),
        (List(ironChoice -> 32, MaterialChoice(Material.STONE_PRESSURE_PLATE) -> 8, MaterialChoice(Material.REDSTONE) -> 8), Material.DETECTOR_RAIL, (8, 16), "Make Detector Rail"),
        (List(ironChoice -> 64), Material.MINECART, (16, 24), "Make Minecarts"),
    )
    val recipes = pairs.flatMap { (ingredients, output, outputCounts, name) =>
        val (lo, hi) = outputCounts
        List(
            Recipe(s"${name} (low people, low efficiency)", ingredients, List(ItemStack(output, lo)), 10, 1),
            Recipe(s"${name} (high people, high efficiency)", ingredients, List(ItemStack(output, hi)), 15, 2),
        )
    }
    val template = CustomItemStack.make(NamespacedKey("ballcore", "rail_manufactory"), Material.PISTON, txt"Rail Manufactory", txt"Crafts metals into rails at bulk rates")

class RailManufactory()(using act: CraftingActor, p: Plugin, prompts: Prompts) extends CraftingStation(RailManufactory.recipes):
    def group = CraftingStations.group
    def template = RailManufactory.template
