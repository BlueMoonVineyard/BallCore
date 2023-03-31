// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import BallCore.CustomItems.CustomItemStack
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.ItemGroup
import org.bukkit.Server

object CardinalOres:
    import Helpers._

    object ItemStacks:
        // north
        val silver = ironLike("silver", "Silver")
        val sapphire = CustomItemStack.make(NamespacedKey("ballcore", "sapphire"), Material.LAPIS_LAZULI, "&rSapphire")

        // south
        val sillicon = ironLike("sillicon", "Sillicon")
        val diamond = CustomItemStack.make(NamespacedKey("ballcore", "diamond"), Material.DIAMOND, "&rDiamond")

        // east
        val cobalt = ironLike("cobalt", "Cobalt")
        val plutonium = CustomItemStack.make(NamespacedKey("ballcore", "plutonium"), Material.AMETHYST_SHARD, "&rPlutonium")

        // west
        val lead = ironLike("lead", "Lead")
        val emerald = CustomItemStack.make(NamespacedKey("ballcore", "emerald"), Material.EMERALD, "&rEmerald")

    val group = ItemGroup(NamespacedKey("ballcore", "cardinal_ores"), ItemStack(Material.IRON_INGOT))
    def registerItems()(using registry: ItemRegistry, server: Server): Unit =
        register(group, ItemStacks.silver)
        register(group, ItemStacks.sapphire)

        register(group, ItemStacks.sillicon)
        register(group, ItemStacks.diamond)

        register(group, ItemStacks.cobalt)
        register(group, ItemStacks.plutonium)

        register(group, ItemStacks.lead)
        register(group, ItemStacks.emerald)
