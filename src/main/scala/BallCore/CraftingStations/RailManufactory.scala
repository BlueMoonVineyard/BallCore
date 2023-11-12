// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.Ores.QuadrantOres.ItemStacks.{goldLikes, ironLikes}
import BallCore.UI.Elements.*
import BallCore.UI.Prompts
import org.bukkit.inventory.RecipeChoice.{ExactChoice, MaterialChoice}
import org.bukkit.inventory.{ItemStack, RecipeChoice}
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}

import scala.jdk.CollectionConverters.*

object RailManufactory:
  private val ironChoice: ExactChoice = ExactChoice(
    ironLikes.map(_.ingot).asJava
  )
  private val goldChoice: ExactChoice = ExactChoice(
    goldLikes.map(_.ingot).asJava
  )
  val pairs: List[(List[(RecipeChoice, Int)], Material, (Int, Int), String)] =
    List(
      (
        List(ironChoice -> 64, MaterialChoice(Material.STICK) -> 8),
        Material.RAIL,
        (256, 342),
        "Make Rail"
      ),
      (
        List(
          goldChoice -> 64,
          MaterialChoice(Material.STICK) -> 8,
          MaterialChoice(Material.REDSTONE) -> 8
        ),
        Material.POWERED_RAIL,
        (256, 342),
        "Make Powered Rail"
      ),
      (
        List(
          ironChoice -> 32,
          MaterialChoice(Material.STONE_PRESSURE_PLATE) -> 8,
          MaterialChoice(Material.REDSTONE) -> 8
        ),
        Material.DETECTOR_RAIL,
        (8, 16),
        "Make Detector Rail"
      ),
      (List(ironChoice -> 64), Material.MINECART, (16, 24), "Make Minecarts")
    )
  val recipes: List[Recipe] = pairs.flatMap {
    (ingredients, output, outputCounts, name) =>
      val (lo, hi) = outputCounts
      List(
        Recipe(
          s"$name (low people, low efficiency)",
          ingredients,
          List(ItemStack(output, lo)),
          10,
          1
        ),
        Recipe(
          s"$name (high people, high efficiency)",
          ingredients,
          List(ItemStack(output, hi)),
          15,
          2
        )
      )
  }
  val template: CustomItemStack = CustomItemStack.make(
    NamespacedKey("ballcore", "rail_manufactory"),
    Material.PISTON,
    txt"Rail Manufactory",
    txt"Crafts metals into rails at bulk rates"
  )

class RailManufactory()(using act: CraftingActor, p: Plugin, prompts: Prompts)
  extends CraftingStation(RailManufactory.recipes):
  def group: ItemGroup = CraftingStations.group

  def template: CustomItemStack = RailManufactory.template
