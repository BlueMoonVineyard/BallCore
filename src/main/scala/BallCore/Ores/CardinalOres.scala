// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import BallCore.CustomItems.{CustomItemStack, ItemGroup, ItemRegistry}
import BallCore.UI.Elements.*
import org.bukkit.inventory.ItemStack
import org.bukkit.{Material, NamespacedKey, Server}
import scala.util.chaining._

object CardinalOres:

    import Helpers.*

    object ItemStacks:
        // north
        val sulfur: OreVariants =
            ironLike("sulfur", "Sulfur", OreTypes.sulfur.num)
        val sapphire: CustomItemStack = CustomItemStack.make(
            NamespacedKey("ballcore", "sapphire"),
            Material.LAPIS_LAZULI,
            txt"Sapphire",
        )
        sapphire.setItemMeta(
            sapphire.getItemMeta().tap(_.setCustomModelData(1))
        )

        // south
        val sillicon: OreVariants =
            ironLike("sillicon", "Sillicon", OreTypes.sillicon.num)
        val diamond: CustomItemStack = CustomItemStack.make(
            NamespacedKey("ballcore", "diamond"),
            Material.DIAMOND,
            txt"Diamond",
        )
        diamond.setItemMeta(diamond.getItemMeta().tap(_.setCustomModelData(1)))

        // east
        val cobalt: OreVariants =
            ironLike("cobalt", "Cobalt", OreTypes.cobalt.num)
        val plutonium: CustomItemStack = CustomItemStack.make(
            NamespacedKey("ballcore", "plutonium"),
            Material.AMETHYST_SHARD,
            txt"Plutonium",
        )
        plutonium.setItemMeta(
            plutonium.getItemMeta().tap(_.setCustomModelData(1))
        )

        // west
        val lead: OreVariants = ironLike("lead", "Lead", OreTypes.lead.num)
        val emerald: CustomItemStack = CustomItemStack.make(
            NamespacedKey("ballcore", "emerald"),
            Material.EMERALD,
            txt"Emerald",
        )
        emerald.setItemMeta(emerald.getItemMeta().tap(_.setCustomModelData(1)))

        val blackOres = List(sulfur, sillicon, cobalt, lead).map(_.raw)
        val blueOres = List(sapphire, diamond, plutonium, emerald)

    val group: ItemGroup = ItemGroup(
        NamespacedKey("ballcore", "cardinal_ores"),
        ItemStack(Material.IRON_INGOT),
    )

    def registerItems()(using registry: ItemRegistry, server: Server): Unit =
        register(group, ItemStacks.sulfur)
        register(group, ItemStacks.sapphire)

        register(group, ItemStacks.sillicon)
        register(group, ItemStacks.diamond)

        register(group, ItemStacks.cobalt)
        register(group, ItemStacks.plutonium)

        register(group, ItemStacks.lead)
        register(group, ItemStacks.emerald)
