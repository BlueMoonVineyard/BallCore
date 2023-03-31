// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.block.Block
import org.bukkit.block.{Furnace => BFurnace}
import org.bukkit.event.inventory.FurnaceBurnEvent
import org.bukkit.event.block.BlockPlaceEvent
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.CustomItemStack
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import BallCore.CustomItems.BlockManager

enum FurnaceTier:
    // tier 0 (vanilla furnace)
    // raw => 1 ingot + 1 depleted
    case Zero

    // raw => 2 ingot + 1 scraps
    // depleted => 1 ingot + 1 scraps
    case One

    // raw => 3 ingot + 1 dust
    // depleted => 2 ingot + 1 dust
    // scraps => 1 ingot + 1 dust
    case Two

    // raw => 4 ingots
    // depleted => 3 ingots
    // scraps => 2 ingots
    // dust => 1 ingot
    case Three

class FurnaceListener(using bm: BlockManager, registry: ItemRegistry) extends Listener:
    def spawn(from: OreVariants, tier: OreTier, by: Block): Unit =
        val loc = by.getLocation().clone().add(0, 1, 0)
        by.getWorld().dropItem(loc, from.ore(tier))
        // TODO: deposit into chest

    def check(furnaceTier: FurnaceTier, oreTier: OreTier): Option[(Int, Option[OreTier])] =
        (furnaceTier, oreTier) match
            case (FurnaceTier.Zero, OreTier.Raw) =>
                Some(1, Some(OreTier.Depleted))
            case (FurnaceTier.Zero, _) =>
                None
            case (FurnaceTier.One, OreTier.Raw) =>
                Some(2, Some(OreTier.Scraps))
            case (FurnaceTier.One, OreTier.Depleted) =>
                Some(1, Some(OreTier.Scraps))
            case (FurnaceTier.One, _) =>
                None
            case (FurnaceTier.Two, OreTier.Raw) =>
                Some(3, Some(OreTier.Dust))
            case (FurnaceTier.Two, OreTier.Depleted) =>
                Some(2, Some(OreTier.Dust))
            case (FurnaceTier.Two, OreTier.Scraps) =>
                Some(1, Some(OreTier.Dust))
            case (FurnaceTier.Two, _) =>
                None
            case (FurnaceTier.Three, OreTier.Raw) =>
                Some(4, None)
            case (FurnaceTier.Three, OreTier.Depleted) =>
                Some(3, None)
            case (FurnaceTier.Three, OreTier.Scraps) =>
                Some(2, None)
            case (FurnaceTier.Three, OreTier.Dust) =>
                Some(1, None)
            case (FurnaceTier.Three, _) =>
                None

    def tier(of: Block): FurnaceTier =
        val furnaceItem = bm.getCustomItem(of)
        if !furnaceItem.isDefined || !furnaceItem.get.isInstanceOf[Furnace] then
            FurnaceTier.Zero
        else
            furnaceItem.asInstanceOf[Furnace].tier

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onItemSmelt(event: FurnaceSmeltEvent): Unit =
        val smeltingItem = registry.lookup(event.getSource())
        if !smeltingItem.isDefined || !smeltingItem.get.isInstanceOf[Ore] then
            return
        val ore = smeltingItem.get.asInstanceOf[Ore]
        check(tier(event.getBlock()), ore.tier) match
            case None =>
                event.setCancelled(true)
            case Some(num, aux) =>
                event.getResult().setAmount(num)
                aux.map { spawn(ore.variants, _, event.getBlock()) }        

object Furnaces:
    val group = ItemGroup(NamespacedKey("ballcore", "furnaces"), ItemStack(Material.WHITE_CONCRETE))

object Furnace:
    val tierOneLore = "&r&fCapable of smelting ores with increased efficiency"

    val ironFurnace = CustomItemStack.make(NamespacedKey("ballcore", "iron_furnace"), Material.FURNACE, "Iron Furnace", tierOneLore)
    val tinFurnace = CustomItemStack.make(NamespacedKey("ballcore", "tin_furnace"), Material.FURNACE, "Tin Furnace", tierOneLore)
    val aluminumFurnace = CustomItemStack.make(NamespacedKey("ballcore", "aluminum_furnace"), Material.FURNACE, "Aluminum Furnace", tierOneLore)
    val zincFurnace = CustomItemStack.make(NamespacedKey("ballcore", "zinc_furnace"), Material.FURNACE, "Zinc Furnace", tierOneLore)

    val tierOne = List(ironFurnace, tinFurnace, aluminumFurnace, zincFurnace)

    val tierTwoLore = "&r&fCapable of smelting ores with astounding efficiency"

    val entschloseniteFurnace = CustomItemStack.make(NamespacedKey("ballcore", "entschlossenite_furnace"), Material.FURNACE, "Entschlossenite Furnace", tierTwoLore)

    val tierTwo = List(entschloseniteFurnace)

    val tierThreeLore = "&r&fCapable of smelting ores with supernatural efficiency"

    val praecantatioFurnace = CustomItemStack.make(NamespacedKey("ballcore", "praecantatio_furnace"), Material.BLAST_FURNACE, "Praecantatio Furnace", tierThreeLore)
    val auramFurnace = CustomItemStack.make(NamespacedKey("ballcore", "auram_furnace"), Material.BLAST_FURNACE, "Auram Furnace", tierThreeLore)
    val alkimiaFurnace = CustomItemStack.make(NamespacedKey("ballcore", "alkimia_furnace"), Material.BLAST_FURNACE, "Alkimia Furnace", tierThreeLore)

    val tierThree = List(praecantatioFurnace, auramFurnace, alkimiaFurnace)

    def registerItems()(using bm: BlockManager, registry: ItemRegistry, server: Server, plugin: JavaPlugin): Unit =
        server.getPluginManager().registerEvents(FurnaceListener(), plugin)
        List((FurnaceTier.One, tierOne), (FurnaceTier.Two, tierTwo), (FurnaceTier.Three, tierThree))
            .foreach { (tier, items) => items.foreach { item => registry.register(Furnace(tier, item)) } }

class Furnace(furnaceTier: FurnaceTier, items: CustomItemStack) extends CustomItem:
    def group = Furnaces.group
    def template = items
    val tier = furnaceTier
