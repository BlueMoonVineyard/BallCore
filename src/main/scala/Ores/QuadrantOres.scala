package BallCore.Ores

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import org.bukkit.inventory.ItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType

object QuadrantOres:
    import Helpers._

    object ItemStacks:
        // +, + base ores
        val iron = ironLike("IRON", "Iron")
        val gold = goldLike("GOLD", "Gold")
        val copper = copperLike("COPPER", "Copper")

        // +, - base ores
        val tin = ironLike("TIN", "Tin")
        val sulfur = goldLike("SULFUR", "Sulfur")
        val orichalcum = copperLike("ORICHALCUM", "Orichalcum")

        // -, - base ores
        val aluminum = ironLike("ALUMINUM", "Aluminum")
        val palladium = goldLike("PALLADIUM", "Palladium")
        val hihiirogane = copperLike("HIHIIROGANE", "Hihi'irogane")

        // -, + base ores
        val zinc = ironLike("ZINC", "Zinc")
        val magnesium = goldLike("MAGNESIUM", "Magnesium")
        val meteorite = copperLike("METEORITE", "Meteorite")

    val group = ItemGroup(NamespacedKey("ballcore", "quadrant_ores"), ItemStack(Material.IRON_INGOT))

    def registerItems()(using plugin: SlimefunAddon): Unit =
        register(group, ItemStacks.iron)
        register(group, ItemStacks.gold)
        register(group, ItemStacks.copper)
        register(group, ItemStacks.tin)
        register(group, ItemStacks.sulfur)
        register(group, ItemStacks.orichalcum)
        register(group, ItemStacks.aluminum)
        register(group, ItemStacks.palladium)
        register(group, ItemStacks.hihiirogane)
        register(group, ItemStacks.zinc)
        register(group, ItemStacks.magnesium)
        register(group, ItemStacks.meteorite)