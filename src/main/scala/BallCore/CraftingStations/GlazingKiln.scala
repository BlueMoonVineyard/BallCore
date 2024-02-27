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

object GlazingKiln:
    val dyes: List[(Material, Material)] = List(
        (Material.WHITE_DYE, Material.WHITE_TERRACOTTA),
        (Material.ORANGE_DYE, Material.ORANGE_TERRACOTTA),
        (Material.MAGENTA_DYE, Material.MAGENTA_TERRACOTTA),
        (Material.LIGHT_BLUE_DYE, Material.LIGHT_BLUE_TERRACOTTA),
        (Material.YELLOW_DYE, Material.YELLOW_TERRACOTTA),
        (Material.LIME_DYE, Material.LIME_TERRACOTTA),
        (Material.PINK_DYE, Material.PINK_TERRACOTTA),
        (Material.GRAY_DYE, Material.GRAY_TERRACOTTA),
        (Material.LIGHT_GRAY_DYE, Material.LIGHT_GRAY_TERRACOTTA),
        (Material.CYAN_DYE, Material.CYAN_TERRACOTTA),
        (Material.PURPLE_DYE, Material.PURPLE_TERRACOTTA),
        (Material.BLUE_DYE, Material.BLUE_TERRACOTTA),
        (Material.BROWN_DYE, Material.BROWN_TERRACOTTA),
        (Material.GREEN_DYE, Material.GREEN_TERRACOTTA),
        (Material.RED_DYE, Material.RED_TERRACOTTA),
        (Material.BLACK_DYE, Material.BLACK_TERRACOTTA),
    )
    val glazes: List[(Material, Material)] = List(
        (Material.WHITE_DYE, Material.WHITE_GLAZED_TERRACOTTA),
        (Material.ORANGE_DYE, Material.ORANGE_GLAZED_TERRACOTTA),
        (Material.MAGENTA_DYE, Material.MAGENTA_GLAZED_TERRACOTTA),
        (Material.LIGHT_BLUE_DYE, Material.LIGHT_BLUE_GLAZED_TERRACOTTA),
        (Material.YELLOW_DYE, Material.YELLOW_GLAZED_TERRACOTTA),
        (Material.LIME_DYE, Material.LIME_GLAZED_TERRACOTTA),
        (Material.PINK_DYE, Material.PINK_GLAZED_TERRACOTTA),
        (Material.GRAY_DYE, Material.GRAY_GLAZED_TERRACOTTA),
        (Material.LIGHT_GRAY_DYE, Material.LIGHT_GRAY_GLAZED_TERRACOTTA),
        (Material.CYAN_DYE, Material.CYAN_GLAZED_TERRACOTTA),
        (Material.PURPLE_DYE, Material.PURPLE_GLAZED_TERRACOTTA),
        (Material.BLUE_DYE, Material.BLUE_GLAZED_TERRACOTTA),
        (Material.BROWN_DYE, Material.BROWN_GLAZED_TERRACOTTA),
        (Material.GREEN_DYE, Material.GREEN_GLAZED_TERRACOTTA),
        (Material.RED_DYE, Material.RED_GLAZED_TERRACOTTA),
        (Material.BLACK_DYE, Material.BLACK_GLAZED_TERRACOTTA),
    )

    val recipes: List[Recipe] = dyes.map { (dye, terracotta) =>
        val key = terracotta.getKey().toString().replace(':', '_')
        Recipe(
            trans"recipes.dye-terracotta".args(terracotta.asComponent),
            NamespacedKey("ballcore", s"dye_$key"),
            List(
                (Vanilla(dye), 4),
                (Vanilla(Material.TERRACOTTA), 64),
            ),
            List((ItemStack(terracotta), 64)),
            10,
            1,
        )
    } concat glazes.map { (dye, glazed) =>
        val key = glazed.getKey().toString().replace(':', '_')
        Recipe(
            trans"recipes.glaze-terracotta".args(glazed.asComponent),
            NamespacedKey("ballcore", s"glaze_$key"),
            List(
                (Vanilla(dye), 4),
                (Vanilla(Material.TERRACOTTA), 64),
            ),
            List((ItemStack(glazed), 64)),
            10,
            1,
        )
    }
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "glazing_kiln"),
        Material.SMOKER,
        trans"items.glazing-kiln",
        trans"items.glazing-kiln.lore",
    )

class GlazingKiln()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(GlazingKiln.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = GlazingKiln.template
