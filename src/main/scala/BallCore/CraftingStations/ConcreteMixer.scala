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

object ConcreteMixer:
  val pairs: List[(Material, Material, Material, String, String)] = List(
    (
      Material.ORANGE_DYE,
      Material.ORANGE_CONCRETE_POWDER,
      Material.ORANGE_CONCRETE,
      "Mix Orange Concrete",
      "Harden Orange Concrete"
    ),
    (
      Material.MAGENTA_DYE,
      Material.MAGENTA_CONCRETE_POWDER,
      Material.MAGENTA_CONCRETE,
      "Mix Magenta Concrete",
      "Harden Magenta Concrete"
    ),
    (
      Material.LIGHT_BLUE_DYE,
      Material.LIGHT_BLUE_CONCRETE_POWDER,
      Material.LIGHT_BLUE_CONCRETE,
      "Mix Light Blue Concrete",
      "Harden Light Blue Concrete"
    ),
    (
      Material.YELLOW_DYE,
      Material.YELLOW_CONCRETE_POWDER,
      Material.YELLOW_CONCRETE,
      "Mix Yellow Concrete",
      "Harden Yellow Concrete"
    ),
    (
      Material.LIME_DYE,
      Material.LIME_CONCRETE_POWDER,
      Material.LIME_CONCRETE,
      "Mix Lime Concrete",
      "Harden Lime Concrete"
    ),
    (
      Material.PINK_DYE,
      Material.PINK_CONCRETE_POWDER,
      Material.PINK_CONCRETE,
      "Mix Pink Concrete",
      "Harden Pink Concrete"
    ),
    (
      Material.GRAY_DYE,
      Material.GRAY_CONCRETE_POWDER,
      Material.GRAY_CONCRETE,
      "Mix Gray Concrete",
      "Harden Gray Concrete"
    ),
    (
      Material.LIGHT_GRAY_DYE,
      Material.LIGHT_GRAY_CONCRETE_POWDER,
      Material.LIGHT_GRAY_CONCRETE,
      "Mix Light Gray Concrete",
      "Harden Light Gray Concrete"
    ),
    (
      Material.CYAN_DYE,
      Material.CYAN_CONCRETE_POWDER,
      Material.CYAN_CONCRETE,
      "Mix Cyan Concrete",
      "Harden Cyan Concrete"
    ),
    (
      Material.PURPLE_DYE,
      Material.PURPLE_CONCRETE_POWDER,
      Material.PURPLE_CONCRETE,
      "Mix Purple Concrete",
      "Harden Purple Concrete"
    ),
    (
      Material.BLUE_DYE,
      Material.BLUE_CONCRETE_POWDER,
      Material.BLUE_CONCRETE,
      "Mix Blue Concrete",
      "Harden Blue Concrete"
    ),
    (
      Material.BROWN_DYE,
      Material.BROWN_CONCRETE_POWDER,
      Material.BROWN_CONCRETE,
      "Mix Brown Concrete",
      "Harden Brown Concrete"
    ),
    (
      Material.GREEN_DYE,
      Material.GREEN_CONCRETE_POWDER,
      Material.GREEN_CONCRETE,
      "Mix Green Concrete",
      "Harden Green Concrete"
    ),
    (
      Material.RED_DYE,
      Material.RED_CONCRETE_POWDER,
      Material.RED_CONCRETE,
      "Mix Red Concrete",
      "Harden Red Concrete"
    ),
    (
      Material.BLACK_DYE,
      Material.BLACK_CONCRETE_POWDER,
      Material.BLACK_CONCRETE,
      "Mix Black Concrete",
      "Harden Black Concrete"
    )
  )
  val recipes: List[Recipe] = pairs.flatMap { it =>
    val (dye, concretePowder, concrete, concretePowderName, concreteName) = it
    List(
      Recipe(
        concretePowderName,
        List(
          (MaterialChoice(dye), 4),
          (MaterialChoice(Material.SAND), 16),
          (MaterialChoice(Material.GRAVEL), 16)
        ),
        List(ItemStack(concretePowder, 64)),
        10,
        1
      ),
      Recipe(
        concreteName,
        List(
          (MaterialChoice(concretePowder), 64)
        ),
        List(ItemStack(concrete, 64)),
        30,
        1
      )
    )
  }
  val template: CustomItemStack = CustomItemStack.make(
    NamespacedKey("ballcore", "concrete_mixer"),
    Material.DECORATED_POT,
    txt"Concrete Mixer",
    txt"Mixes and hardens concrete with greater efficiency"
  )

class ConcreteMixer()(using act: CraftingActor, p: Plugin, prompts: Prompts)
  extends CraftingStation(ConcreteMixer.recipes):
  def group: ItemGroup = CraftingStations.group

  def template: CustomItemStack = ConcreteMixer.template
