// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Gear

import BallCore.CustomItems.{
    CustomItemStack,
    ItemGroup,
    ItemRegistry,
    PlainCustomItem,
}
import BallCore.UI.Elements.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.{ItemFlag, ItemStack, ShapedRecipe}
import org.bukkit.{Material, NamespacedKey}

import scala.util.chaining.*

trait CustomModelDatas:
    val num: Int

enum IronToolSetCustomModelDatas(val num: Int) extends CustomModelDatas:
    case iron extends IronToolSetCustomModelDatas(1)
    case copper extends IronToolSetCustomModelDatas(2)
    case tin extends IronToolSetCustomModelDatas(3)
    case orichalcum extends IronToolSetCustomModelDatas(4)
    case aluminum extends IronToolSetCustomModelDatas(5)
    case hihiirogane extends IronToolSetCustomModelDatas(6)
    case zinc extends IronToolSetCustomModelDatas(7)
    case meteorite extends IronToolSetCustomModelDatas(8)
    case bronze extends IronToolSetCustomModelDatas(9)
    case gildedIron extends IronToolSetCustomModelDatas(10)
    case magnox extends IronToolSetCustomModelDatas(11)
    case pallalumin extends IronToolSetCustomModelDatas(12)

enum GoldToolSetCustomModelDatas(val num: Int) extends CustomModelDatas:
    case gold extends GoldToolSetCustomModelDatas(1)
    case silver extends GoldToolSetCustomModelDatas(2)
    case palladium extends GoldToolSetCustomModelDatas(3)
    case magnesium extends GoldToolSetCustomModelDatas(4)

enum DiamondToolSetCustomModelDatas(val num: Int) extends CustomModelDatas:
    case skyBronzeMorning extends DiamondToolSetCustomModelDatas(1)
    case skyBronzeDay extends DiamondToolSetCustomModelDatas(2)
    case skyBronzeEvening extends DiamondToolSetCustomModelDatas(3)
    case skyBronzeNight extends DiamondToolSetCustomModelDatas(4)

enum ToolSet[E <: CustomModelDatas](
    val pick: Material,
    val axe: Material,
    val shovel: Material,
    val hoe: Material,
    val sword: Material,
    val helmet: Material,
    val chestplate: Material,
    val leggings: Material,
    val boots: Material,
):
    case Iron
        extends ToolSet[IronToolSetCustomModelDatas](
            Material.IRON_PICKAXE,
            Material.IRON_AXE,
            Material.IRON_SHOVEL,
            Material.IRON_HOE,
            Material.IRON_SWORD,
            Material.IRON_HELMET,
            Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS,
            Material.IRON_BOOTS,
        )
    case Gold
        extends ToolSet[GoldToolSetCustomModelDatas](
            Material.GOLDEN_PICKAXE,
            Material.GOLDEN_AXE,
            Material.GOLDEN_SHOVEL,
            Material.GOLDEN_HOE,
            Material.GOLDEN_SWORD,
            Material.GOLDEN_HELMET,
            Material.GOLDEN_CHESTPLATE,
            Material.GOLDEN_LEGGINGS,
            Material.GOLDEN_BOOTS,
        )
    case Diamond
        extends ToolSet[DiamondToolSetCustomModelDatas](
            Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL,
            Material.DIAMOND_HOE,
            Material.DIAMOND_SWORD,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
        )

object Gear:
    val group: ItemGroup = ItemGroup(
        NamespacedKey("ballcore", "gear"),
        ItemStack(Material.DIAMOND_PICKAXE),
    )

    private def hide(s: ItemStack): Unit =
        val meta = s.getItemMeta
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        val _ = s.setItemMeta(meta)

    private def pickaxe[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_pickaxe"),
            base.pick,
            txt"$name Pickaxe",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(
                NamespacedKey("bc", s"${id.toLowerCase()}_pickaxe"),
                is,
            )
        recipe.shape(
            "III",
            " S ",
            " S ",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)

    private def axe[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_axe"),
            base.axe,
            txt"$name Axe",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_axe"), is)
        recipe.shape(
            "II",
            "IS",
            " S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)

    private def shovel[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_shovel"),
            base.shovel,
            txt"$name Shovel",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_shovel"), is)
        recipe.shape(
            "I",
            "S",
            "S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)

    private def hoe[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_hoe"),
            base.hoe,
            txt"$name Hoe",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_hoe"), is)
        recipe.shape(
            "II",
            " S",
            " S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)

    def tools[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        pickaxe(base, ore, name, id, cmd, enchants: _*)
        axe(base, ore, name, id, cmd, enchants: _*)
        shovel(base, ore, name, id, cmd, enchants: _*)
        hoe(base, ore, name, id, cmd, enchants: _*)

    def sword[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_sword"),
            base.sword,
            txt"$name Sword",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_sword"), is)
        recipe.shape(
            "I",
            "I",
            "S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)

    private def helmet[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_helmet"),
            base.helmet,
            txt"$name Helmet",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_helmet"), is)
        recipe.shape(
            "III",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)

    private def chestplate[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_chestplate"),
            base.chestplate,
            txt"$name Chestplate",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(
                NamespacedKey("bc", s"${id.toLowerCase()}_chestplate"),
                is,
            )
        recipe.shape(
            "I I",
            "III",
            "III",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)

    private def leggings[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_leggings"),
            base.leggings,
            txt"$name Leggings",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(
                NamespacedKey("bc", s"${id.toLowerCase()}_leggings"),
                is,
            )
        recipe.shape(
            "III",
            "I I",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)

    private def boots[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"${id}_boots"),
            base.boots,
            txt"$name Boots",
        )
        register(is, cmd, enchants)

        val recipe =
            ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_boots"), is)
        recipe.shape(
            "I I",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)

    def armor[E <: CustomModelDatas](
        base: ToolSet[E],
        ore: ItemStack,
        name: String,
        id: String,
        cmd: E,
        enchants: (Enchantment, Int)*
    )(using registry: ItemRegistry): Unit =
        helmet(base, ore, name, id, cmd, enchants: _*)
        chestplate(base, ore, name, id, cmd, enchants: _*)
        leggings(base, ore, name, id, cmd, enchants: _*)
        boots(base, ore, name, id, cmd, enchants: _*)

    private def register[E <: CustomModelDatas](
        is: CustomItemStack,
        cmd: E,
        enchants: Seq[(Enchantment, Int)],
    )(using
        registry: ItemRegistry
    ): Unit =
        hide(is)
        enchants.foreach {
            // noinspection ConvertibleToMethodValue
            is.addUnsafeEnchantment(_, _)
        }
        is.setItemMeta(is.getItemMeta.tap(_.setCustomModelData(cmd.num)))
        val it = PlainCustomItem(group, is)
        registry.register(it)
