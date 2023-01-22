package BallCore.Ores

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.Material
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import me.mrCookieSlime.Slimefun.api.BlockStorage
import org.bukkit.block.Block
import org.bukkit.block.{Furnace => BFurnace}
import org.bukkit.event.inventory.FurnaceBurnEvent
import org.bukkit.event.block.BlockPlaceEvent

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

object FurnaceListener extends Listener:
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
        val furnaceItem = BlockStorage.check(of)
        if !furnaceItem.isInstanceOf[Furnace] then
            FurnaceTier.Zero
        else
            furnaceItem.asInstanceOf[Furnace].tier

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onItemSmelt(event: FurnaceSmeltEvent): Unit =
        val smeltingItem = SlimefunItem.getByItem(event.getSource())
        if !smeltingItem.isInstanceOf[Ore] then
            return
        val ore = smeltingItem.asInstanceOf[Ore]
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

    val ironFurnace = SlimefunItemStack("BC_IRON_FURNACE", Material.FURNACE, "Iron Furnace", tierOneLore)
    val tinFurnace = SlimefunItemStack("BC_TIN_FURNACE", Material.FURNACE, "Tin Furnace", tierOneLore)
    val aluminumFurnace = SlimefunItemStack("BC_ALUMINUM_FURNACE", Material.FURNACE, "Aluminum Furnace", tierOneLore)
    val zincFurnace = SlimefunItemStack("BC_ZINC_FURNACE", Material.FURNACE, "Zinc Furnace", tierOneLore)

    val tierOne = List(ironFurnace, tinFurnace, aluminumFurnace, zincFurnace)

    val tierTwoLore = "&r&fCapable of smelting ores with astounding efficiency"

    val entschloseniteFurnace = SlimefunItemStack("BC_ENTSCHLOSSENITE_FURNACE", Material.FURNACE, "Entschlossenite Furnace", tierTwoLore)

    val tierTwo = List(entschloseniteFurnace)

    val tierThreeLore = "&r&fCapable of smelting ores with supernatural efficiency"

    val praecantatioFurnace = SlimefunItemStack("BC_PRAECANTATIO_FURNACE", Material.BLAST_FURNACE, "Praecantatio Furnace", tierThreeLore)
    val auramFurnace = SlimefunItemStack("BC_AURAM_FURNACE", Material.BLAST_FURNACE, "Auram Furnace", tierThreeLore)
    val alkimiaFurnace = SlimefunItemStack("BC_ALKIMIA_FURNACE", Material.BLAST_FURNACE, "Alkimia Furnace", tierThreeLore)

    val tierThree = List(praecantatioFurnace, auramFurnace, alkimiaFurnace)

    def registerItems()(using sf: SlimefunAddon): Unit =
        sf.getJavaPlugin().getServer().getPluginManager().registerEvents(FurnaceListener, sf.getJavaPlugin())
        List((FurnaceTier.One, tierOne), (FurnaceTier.Two, tierTwo), (FurnaceTier.Three, tierThree))
            .foreach { (tier, items) => items.foreach { Furnace(tier, _).register(sf) } }

class Furnace(furnaceTier: FurnaceTier, items: SlimefunItemStack)
    extends SlimefunItem(Furnaces.group, items, RecipeType.NULL, null):

    val tier = furnaceTier
