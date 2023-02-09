// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon

object CardinalOres:
    import Helpers._

    object ItemStacks:
        // north
        val silver = ironLike("SILVER", "Silver")
        val sapphire = SlimefunItemStack("BC_SAPPHIRE", Material.LAPIS_LAZULI, "&rSapphire")

        // south
        val sillicon = ironLike("SILLICON", "Sillicon")
        val diamond = SlimefunItemStack("BC_DIAMOND", Material.DIAMOND, "&rDiamond")

        // east
        val cobalt = ironLike("COBALT", "Cobalt")
        val plutonium = SlimefunItemStack("BC_PLUTONIUM", Material.AMETHYST_SHARD, "&rPlutonium")

        // west
        val lead = ironLike("LEAD", "Lead")
        val emerald = SlimefunItemStack("BC_EMERALD", Material.EMERALD, "&rEmerald")

    val group = ItemGroup(NamespacedKey("ballcore", "cardinal_ores"), ItemStack(Material.IRON_INGOT))
    def registerItems()(using plugin: SlimefunAddon): Unit =
        register(group, ItemStacks.silver)
        register(group, ItemStacks.sapphire)

        register(group, ItemStacks.sillicon)
        register(group, ItemStacks.diamond)

        register(group, ItemStacks.cobalt)
        register(group, ItemStacks.plutonium)

        register(group, ItemStacks.lead)
        register(group, ItemStacks.emerald)