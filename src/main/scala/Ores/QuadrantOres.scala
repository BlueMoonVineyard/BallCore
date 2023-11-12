// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import BallCore.CustomItems.{ItemGroup, ItemRegistry}
import org.bukkit.inventory.ItemStack
import org.bukkit.{Material, NamespacedKey, Server}

object QuadrantOres:

  import Helpers.*

  object ItemStacks:
    // +, + base ores
    val iron = ironLike("iron", "Iron", OreTypes.iron.num)
    val gold = goldLike("gold", "Gold", OreTypes.gold.num)
    val orichalcum =
      copperLike("orichalcum", "Orichalcum", OreTypes.orichalcum.num)

    // +, - base ores
    val tin = ironLike("tin", "Tin", OreTypes.tin.num)
    val silver = goldLike("silver", "Silver", OreTypes.sulfur.num)
    val copper = copperLike("copper", "Copper", OreTypes.copper.num)

    // -, - base ores
    val aluminum = ironLike("aluminum", "Aluminum", OreTypes.aluminum.num)
    val palladium = goldLike("palladium", "Palladium", OreTypes.palladium.num)
    val hihiirogane =
      copperLike("hihiirogane", "Hihi'irogane", OreTypes.hihiirogane.num)

    // -, + base ores
    val zinc = ironLike("zinc", "Zinc", OreTypes.zinc.num)
    val magnesium = goldLike("magnesium", "Magnesium", OreTypes.magnesium.num)
    val meteorite = copperLike("meteorite", "Meteorite", OreTypes.meteorite.num)

    val ironLikes = List(iron, tin, aluminum, zinc)
    val goldLikes = List(gold, silver, palladium, magnesium)
    val copperLikes = List(copper, orichalcum, hihiirogane, meteorite)

  val group = ItemGroup(
    NamespacedKey("ballcore", "quadrant_ores"),
    ItemStack(Material.IRON_INGOT)
  )

  def registerItems()(using plugin: ItemRegistry, server: Server): Unit =
    register(group, ItemStacks.iron)
    register(group, ItemStacks.gold)
    register(group, ItemStacks.copper)
    register(group, ItemStacks.tin)
    register(group, ItemStacks.silver)
    register(group, ItemStacks.orichalcum)
    register(group, ItemStacks.aluminum)
    register(group, ItemStacks.palladium)
    register(group, ItemStacks.hihiirogane)
    register(group, ItemStacks.zinc)
    register(group, ItemStacks.magnesium)
    register(group, ItemStacks.meteorite)
