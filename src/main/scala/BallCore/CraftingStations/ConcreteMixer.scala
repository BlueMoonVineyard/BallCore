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

object ConcreteMixer:
    val pairs: List[(Material, Material, Material)] = List(
        (
            Material.ORANGE_DYE,
            Material.ORANGE_CONCRETE_POWDER,
            Material.ORANGE_CONCRETE,
        ),
        (
            Material.MAGENTA_DYE,
            Material.MAGENTA_CONCRETE_POWDER,
            Material.MAGENTA_CONCRETE,
        ),
        (
            Material.LIGHT_BLUE_DYE,
            Material.LIGHT_BLUE_CONCRETE_POWDER,
            Material.LIGHT_BLUE_CONCRETE,
        ),
        (
            Material.YELLOW_DYE,
            Material.YELLOW_CONCRETE_POWDER,
            Material.YELLOW_CONCRETE,
        ),
        (
            Material.LIME_DYE,
            Material.LIME_CONCRETE_POWDER,
            Material.LIME_CONCRETE,
        ),
        (
            Material.PINK_DYE,
            Material.PINK_CONCRETE_POWDER,
            Material.PINK_CONCRETE,
        ),
        (
            Material.GRAY_DYE,
            Material.GRAY_CONCRETE_POWDER,
            Material.GRAY_CONCRETE,
        ),
        (
            Material.LIGHT_GRAY_DYE,
            Material.LIGHT_GRAY_CONCRETE_POWDER,
            Material.LIGHT_GRAY_CONCRETE,
        ),
        (
            Material.CYAN_DYE,
            Material.CYAN_CONCRETE_POWDER,
            Material.CYAN_CONCRETE,
        ),
        (
            Material.PURPLE_DYE,
            Material.PURPLE_CONCRETE_POWDER,
            Material.PURPLE_CONCRETE,
        ),
        (
            Material.BLUE_DYE,
            Material.BLUE_CONCRETE_POWDER,
            Material.BLUE_CONCRETE,
        ),
        (
            Material.BROWN_DYE,
            Material.BROWN_CONCRETE_POWDER,
            Material.BROWN_CONCRETE,
        ),
        (
            Material.GREEN_DYE,
            Material.GREEN_CONCRETE_POWDER,
            Material.GREEN_CONCRETE,
        ),
        (
            Material.RED_DYE,
            Material.RED_CONCRETE_POWDER,
            Material.RED_CONCRETE,
        ),
        (
            Material.BLACK_DYE,
            Material.BLACK_CONCRETE_POWDER,
            Material.BLACK_CONCRETE,
        ),
    )
    val recipes: List[Recipe] = pairs.flatMap { it =>
        val (dye, concretePowder, concrete) =
            it

        val cpKey = concretePowder.getKey().toString().replace(':', '_')
        val cKey = concrete.getKey().toString().replace(':', '_')

        List(
            Recipe(
                trans"recipes.mix-concrete-powder".args(concretePowder.asComponent),
                NamespacedKey("ballcore", s"mix_$cpKey"),
                List(
                    (Vanilla(dye), 4),
                    (Vanilla(Material.SAND), 16),
                    (Vanilla(Material.GRAVEL), 16),
                ),
                List((ItemStack(concretePowder), 64)),
                10,
                1,
            ),
            Recipe(
                trans"recipes.harden-concrete".args(concrete.asComponent),
                NamespacedKey("ballcore", s"harden_$cKey"),
                List(
                    (Vanilla(concretePowder), 64)
                ),
                List((ItemStack(concrete), 64)),
                30,
                1,
            ),
        )
    }
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "concrete_mixer"),
        Material.DECORATED_POT,
        trans"items.concrete-mixer",
        trans"items.concrete-mixer.lore",
    )

class ConcreteMixer()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(ConcreteMixer.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = ConcreteMixer.template
