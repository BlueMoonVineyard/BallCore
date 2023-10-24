// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Gear

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.enchantments.Enchantment
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.RecipeChoice.ExactChoice
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.CustomItemStack
import BallCore.CustomItems.PlainCustomItem
import BallCore.UI.Elements._

enum ToolSet(
    _pick: Material,
    _axe: Material,
    _shovel: Material,
    _hoe: Material,
    _sword: Material,
    _helmet: Material,
    _chestplate: Material,
    _leggings: Material,
    _boots: Material,
):
    case Iron extends ToolSet(Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE, Material.IRON_SWORD, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS)
    case Gold extends ToolSet(Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE, Material.GOLDEN_SWORD, Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS)
    case Diamond extends ToolSet(Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE, Material.DIAMOND_SWORD, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS)

    val pick = _pick
    val axe = _axe
    val shovel = _shovel
    val hoe = _hoe
    val sword = _sword
    val helmet = _helmet
    val chestplate = _chestplate
    val leggings = _leggings
    val boots = _boots

object Gear:
    val group = ItemGroup(NamespacedKey("ballcore", "gear"), ItemStack(Material.DIAMOND_PICKAXE))

    private def hide(s: ItemStack): Unit =
        val meta = s.getItemMeta()
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        val _ = s.setItemMeta(meta)
    def pickaxe(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_pickaxe"), base.pick, txt"${name} Pickaxe")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_pickaxe"), is)
        recipe.shape(
            "III",
            " S ",
            " S ",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)
    def axe(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_axe"), base.axe, txt"${name} Axe")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_axe"), is)
        recipe.shape(
            "II",
            "IS",
            " S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)
    def shovel(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_shovel"), base.shovel, txt"${name} Shovel")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_shovel"), is)
        recipe.shape(
            "I",
            "S",
            "S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)
    def hoe(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_hoe"), base.hoe, txt"${name} Hoe")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_hoe"), is)
        recipe.shape(
            "II",
            " S",
            " S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)
    def tools(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        pickaxe(base, ore, name, id, enchants: _*)
        axe(base, ore, name, id, enchants: _*)
        shovel(base, ore, name, id, enchants: _*)
        hoe(base, ore, name, id, enchants: _*)
    def sword(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_sword"), base.sword, txt"${name} Sword")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_sword"), is)
        recipe.shape(
            "I",
            "I",
            "S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        registry.addRecipe(recipe)
    def helmet(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_helmet"), base.helmet, txt"${name} Helmet")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_helmet"), is)
        recipe.shape(
            "III",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)
    def chestplate(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_chestplate"), base.chestplate, txt"${name} Chestplate")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_chestplate"), is)
        recipe.shape(
            "I I",
            "III",
            "III",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)
    def leggings(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_leggings"), base.leggings, txt"${name} Leggings")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_leggings"), is)
        recipe.shape(
            "III",
            "I I",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)
    def boots(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        val is = CustomItemStack.make(NamespacedKey("ballcore", s"${id}_boots"), base.boots, txt"${name} Boots")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = PlainCustomItem(group, is)
        registry.register(it)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_boots"), is)
        recipe.shape(
            "I I",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        registry.addRecipe(recipe)
    def armor(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using registry: ItemRegistry): Unit =
        helmet(base, ore, name, id, enchants: _*)
        chestplate(base, ore, name, id, enchants: _*)
        leggings(base, ore, name, id, enchants: _*)
        boots(base, ore, name, id, enchants: _*)
