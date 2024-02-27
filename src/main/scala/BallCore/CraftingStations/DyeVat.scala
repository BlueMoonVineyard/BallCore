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

object DyeVat:
    val pairs: List[(Material, Material)] = List(
        (Material.ORANGE_DYE, Material.ORANGE_WOOL),
        (Material.MAGENTA_DYE, Material.MAGENTA_WOOL),
        (Material.LIGHT_BLUE_DYE, Material.LIGHT_BLUE_WOOL),
        (Material.YELLOW_DYE, Material.YELLOW_WOOL),
        (Material.LIME_DYE, Material.LIME_WOOL),
        (Material.PINK_DYE, Material.PINK_WOOL),
        (Material.GRAY_DYE, Material.GRAY_WOOL),
        (Material.LIGHT_GRAY_DYE, Material.LIGHT_GRAY_WOOL),
        (Material.CYAN_DYE, Material.CYAN_WOOL),
        (Material.PURPLE_DYE, Material.PURPLE_WOOL),
        (Material.BLUE_DYE, Material.BLUE_WOOL),
        (Material.BROWN_DYE, Material.BROWN_WOOL),
        (Material.GREEN_DYE, Material.GREEN_WOOL),
        (Material.RED_DYE, Material.RED_WOOL),
        (Material.BLACK_DYE, Material.BLACK_WOOL),
    )
    val recipes: List[Recipe] = pairs.map { (dye, wool) =>
        val key = wool.getKey().toString().replace(':', '_')
        Recipe(
            trans"recipes.dye-wool".args(wool.asComponent),
            NamespacedKey("ballcore", s"dye_$key"),
            List(
                (Vanilla(dye), 4),
                (Vanilla(Material.WHITE_WOOL), 64),
            ),
            List((ItemStack(wool), 64)),
            10,
            1,
        )
    }
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "dye_vat"),
        Material.CAULDRON,
        trans"items.dye-vat",
        trans"items.dye-vat.lore",
    )

class DyeVat()(using CraftingActor, Plugin, Prompts, SQLManager, ItemRegistry)
    extends CraftingStation(DyeVat.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = DyeVat.template
