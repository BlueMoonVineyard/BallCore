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
import BallCore.UI.Elements._

object CardinalOres:
    import Helpers._

    object ItemStacks:
        // north
        val sulfur = ironLike("sulfur", "Sulfur", OreTypes.sulfur.num)
        val sapphire = CustomItemStack.make(NamespacedKey("ballcore", "sapphire"), Material.LAPIS_LAZULI, txt"Sapphire")

        // south
        val sillicon = ironLike("sillicon", "Sillicon", OreTypes.sillicon.num)
        val diamond = CustomItemStack.make(NamespacedKey("ballcore", "diamond"), Material.DIAMOND, txt"Diamond")

        // east
        val cobalt = ironLike("cobalt", "Cobalt", OreTypes.cobalt.num)
        val plutonium = CustomItemStack.make(NamespacedKey("ballcore", "plutonium"), Material.AMETHYST_SHARD, txt"Plutonium")

        // west
        val lead = ironLike("lead", "Lead", OreTypes.lead.num)
        val emerald = CustomItemStack.make(NamespacedKey("ballcore", "emerald"), Material.EMERALD, txt"Emerald")

    val group = ItemGroup(NamespacedKey("ballcore", "cardinal_ores"), ItemStack(Material.IRON_INGOT))
    def registerItems()(using registry: ItemRegistry, server: Server): Unit =
        register(group, ItemStacks.sulfur)
        register(group, ItemStacks.sapphire)

        register(group, ItemStacks.sillicon)
        register(group, ItemStacks.diamond)

        register(group, ItemStacks.cobalt)
        register(group, ItemStacks.plutonium)

        register(group, ItemStacks.lead)
        register(group, ItemStacks.emerald)
