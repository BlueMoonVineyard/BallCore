// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.ItemRegistry
import org.bukkit.Server

object QuadrantOres:
    import Helpers._

    object ItemStacks:
        // +, + base ores
        val iron = ironLike("iron", "Iron")
        val gold = goldLike("gold", "Gold")
        val copper = copperLike("copper", "Copper")

        // +, - base ores
        val tin = ironLike("tin", "Tin")
        val sulfur = goldLike("sulfur", "Sulfur")
        val orichalcum = copperLike("orichalcum", "Orichalcum")

        // -, - base ores
        val aluminum = ironLike("aluminum", "Aluminum")
        val palladium = goldLike("palladium", "Palladium")
        val hihiirogane = copperLike("hihiirogane", "Hihi'irogane")

        // -, + base ores
        val zinc = ironLike("zinc", "Zinc")
        val magnesium = goldLike("magnesium", "Magnesium")
        val meteorite = copperLike("meteorite", "Meteorite")

    val group = ItemGroup(NamespacedKey("ballcore", "quadrant_ores"), ItemStack(Material.IRON_INGOT))

    def registerItems()(using plugin: ItemRegistry, server: Server): Unit =
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
