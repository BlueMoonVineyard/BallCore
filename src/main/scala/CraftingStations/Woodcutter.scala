// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CustomItems.CustomItemStack
import BallCore.UI.Elements.*
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey, Tag}

object Woodcutter:
  val woods = List(
    (Tag.OAK_LOGS, Material.OAK_PLANKS),
    (Tag.SPRUCE_LOGS, Material.SPRUCE_PLANKS),
    (Tag.BIRCH_LOGS, Material.BIRCH_PLANKS),
    (Tag.JUNGLE_LOGS, Material.JUNGLE_PLANKS),
    (Tag.ACACIA_LOGS, Material.ACACIA_PLANKS),
    (Tag.DARK_OAK_LOGS, Material.DARK_OAK_PLANKS),
    (Tag.MANGROVE_LOGS, Material.MANGROVE_PLANKS),
    (Tag.CHERRY_LOGS, Material.CHERRY_PLANKS),
    (Tag.CRIMSON_STEMS, Material.CRIMSON_PLANKS),
    (Tag.WARPED_STEMS, Material.WARPED_PLANKS)
  )
  val recipes = woods.flatMap { it =>
    val (input, output) = it

    List(
      Recipe(
        "Process logs into planks (low players, low efficiency)",
        List((MaterialChoice(input), 64)),
        List(ItemStack(output, 64 * 5)),
        10,
        1
      ),
      Recipe(
        "Process logs into planks (medium players, medium efficiency)",
        List((MaterialChoice(input), 64)),
        List(ItemStack(output, 64 * 6)),
        10,
        2
      ),
      Recipe(
        "Process logs into planks (high players, high efficiency)",
        List((MaterialChoice(input), 64)),
        List(ItemStack(output, 64 * 8)),
        20,
        4
      )
    )
  }
  val template = CustomItemStack.make(
    NamespacedKey("ballcore", "woodcutter"),
    Material.STONECUTTER,
    txt"Woodcutter",
    txt"Processes logs with greater efficiency"
  )

class Woodcutter()(using act: CraftingActor, p: Plugin, prompts: Prompts)
  extends CraftingStation(Woodcutter.recipes):
  def group = CraftingStations.group

  def template = Woodcutter.template
