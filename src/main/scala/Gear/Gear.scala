package BallCore.Gear

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.enchantments.Enchantment
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import org.bukkit.NamespacedKey
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.RecipeChoice.ExactChoice

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
    val group = ItemGroup(NamespacedKey("ballcore", "gear"), CustomItemStack(Material.DIAMOND_PICKAXE, "BallCore Gear"))

    private def hide(s: ItemStack): Unit =
        val meta = s.getItemMeta()
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        s.setItemMeta(meta)
    def pickaxe(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_PICKAXE", base.pick, s"&r${name} Pickaxe")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_pickaxe"), is)
        recipe.shape(
            "III",
            " S ",
            " S ",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def axe(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_AXE", base.axe, s"&r${name} Axe")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_axe"), is)
        recipe.shape(
            "II",
            "IS",
            " S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def shovel(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_SHOVEL", base.shovel, s"&r${name} Shovel")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_shovel"), is)
        recipe.shape(
            "I",
            "S",
            "S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def hoe(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_HOE", base.hoe, s"&r${name} Hoe")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_hoe"), is)
        recipe.shape(
            "II",
            " S",
            " S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def tools(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        pickaxe(base, ore, name, id, enchants: _*)
        axe(base, ore, name, id, enchants: _*)
        shovel(base, ore, name, id, enchants: _*)
        hoe(base, ore, name, id, enchants: _*)
    def sword(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_SWORD", base.sword, s"&r${name} Sword")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_sword"), is)
        recipe.shape(
            "I",
            "I",
            "S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def helmet(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_HELMET", base.helmet, s"&r${name} Helmet")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_helmet"), is)
        recipe.shape(
            "III",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def chestplate(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_CHESTPLATE", base.chestplate, s"&r${name} Chestplate")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_chestplate"), is)
        recipe.shape(
            "I I",
            "III",
            "III",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def leggings(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_LEGGINGS", base.leggings, s"&r${name} Leggings")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_leggings"), is)
        recipe.shape(
            "III",
            "I I",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def boots(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        val is = SlimefunItemStack(s"BC_${id}_BOOTS", base.boots, s"&r${name} Boots")
        hide(is)
        enchants.foreach { is.addUnsafeEnchantment(_, _) }
        val it = SlimefunItem(group, is, RecipeType.NULL, null)
        it.register(sf)

        val recipe = ShapedRecipe(NamespacedKey("bc", s"${id.toLowerCase()}_boots"), is)
        recipe.shape(
            "I I",
            "I I",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        sf.getJavaPlugin().getServer().addRecipe(recipe)
    def armor(base: ToolSet, ore: ItemStack, name: String, id: String, enchants: (Enchantment, Int)*)(using sf: SlimefunAddon): Unit =
        helmet(base, ore, name, id, enchants: _*)
        chestplate(base, ore, name, id, enchants: _*)
        leggings(base, ore, name, id, enchants: _*)
        boots(base, ore, name, id, enchants: _*)