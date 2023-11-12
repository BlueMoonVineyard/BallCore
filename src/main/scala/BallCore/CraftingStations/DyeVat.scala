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

object DyeVat:
    val pairs: List[(Material, Material, String)] = List(
        (Material.ORANGE_DYE, Material.ORANGE_WOOL, "Dye Wool Orange"),
        (Material.MAGENTA_DYE, Material.MAGENTA_WOOL, "Dye Wool Magenta"),
        (
            Material.LIGHT_BLUE_DYE,
            Material.LIGHT_BLUE_WOOL,
            "Dye Wool Light Blue",
        ),
        (Material.YELLOW_DYE, Material.YELLOW_WOOL, "Dye Wool Yellow"),
        (Material.LIME_DYE, Material.LIME_WOOL, "Dye Wool Lime"),
        (Material.PINK_DYE, Material.PINK_WOOL, "Dye Wool Pink"),
        (Material.GRAY_DYE, Material.GRAY_WOOL, "Dye Wool Gray"),
        (
            Material.LIGHT_GRAY_DYE,
            Material.LIGHT_GRAY_WOOL,
            "Dye Wool Light Gray",
        ),
        (Material.CYAN_DYE, Material.CYAN_WOOL, "Dye Wool Cyan"),
        (Material.PURPLE_DYE, Material.PURPLE_WOOL, "Dye Wool Purple"),
        (Material.BLUE_DYE, Material.BLUE_WOOL, "Dye Wool Blue"),
        (Material.BROWN_DYE, Material.BROWN_WOOL, "Dye Wool Brown"),
        (Material.GREEN_DYE, Material.GREEN_WOOL, "Dye Wool Green"),
        (Material.RED_DYE, Material.RED_WOOL, "Dye Wool Red"),
        (Material.BLACK_DYE, Material.BLACK_WOOL, "Dye Wool Black"),
    )
    val recipes: List[Recipe] = pairs.map { it =>
        val (dye, wool, name) = it
        Recipe(
            name,
            List(
                (MaterialChoice(dye), 4),
                (MaterialChoice(Material.WHITE_WOOL), 64),
            ),
            List(ItemStack(wool, 64)),
            10,
            1,
        )
    }
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "dye_vat"),
        Material.CAULDRON,
        txt"Dye Vat",
        txt"Dyes more wools with less dyes than normal crafting",
    )

class DyeVat()(using act: CraftingActor, p: Plugin, prompts: Prompts)
    extends CraftingStation(DyeVat.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = DyeVat.template
