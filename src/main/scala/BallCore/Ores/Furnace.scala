// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import BallCore.CraftingStations.WorkChestUtils
import BallCore.CustomItems.*
import BallCore.Storage.SQLManager
import BallCore.UI.Elements.*
import org.bukkit.block.{Block, Furnace as BFurnace}
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Material, NamespacedKey, Server}

import scala.util.chaining.*
import BallCore.Ores.QuadrantOres.ItemStacks
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.RecipeChoice.ExactChoice

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

class FurnaceListener(using
    bm: BlockManager,
    registry: ItemRegistry,
    sql: SQLManager,
) extends Listener:
    def spawn(it: ItemStack, by: Block): Unit =
        val loc = by.getLocation().clone().add(0, 1, 0)
        WorkChestUtils.findWorkChest(by) match
            case None =>
                by.getWorld.dropItem(loc, it); ()
            case Some((chest, inv)) =>
                WorkChestUtils.insertInto(List(it), inv, chest)

    private def oreSmeltingResult(furnaceTier: FurnaceTier): (Int, OreTier) =
        furnaceTier match
            case FurnaceTier.Zero =>
                (4, OreTier.Nugget)
            case FurnaceTier.One =>
                (6, OreTier.Nugget)
            case FurnaceTier.Two =>
                (1, OreTier.Ingot)
            case FurnaceTier.Three =>
                (2, OreTier.Ingot)

    def tier(of: Block): FurnaceTier =
        val furnaceItem = sql.useBlocking(sql.withS(bm.getCustomItem(of)))
        furnaceItem match
            case Some(furnace) if furnaceItem.get.isInstanceOf[Furnace] =>
                furnaceItem.get.asInstanceOf[Furnace].tier
            case _ =>
                FurnaceTier.Zero

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onItemSmelt(event: FurnaceSmeltEvent): Unit =
        val smeltingItem = registry.lookup(event.getSource)
        if smeltingItem.isEmpty || !smeltingItem.get.isInstanceOf[Ore] then
            return
        val ore = smeltingItem.get.asInstanceOf[Ore]
        val (num, kind) = oreSmeltingResult(tier(event.getBlock))

        // WORKAROUND: bukkit code doesn't seem to like custom items all that much
        event.setCancelled(true)
        val result = ore.variants.ore(kind).clone().tap(_.setAmount(num))
        spawn(result, event.getBlock)

        val furnaceState = event.getBlock.getState(false).asInstanceOf[BFurnace]
        val inputSlot = furnaceState.getInventory.getSmelting
            .tap(x => x.setAmount(x.getAmount - 1))
        furnaceState.getInventory.setSmelting(inputSlot)

object Furnaces:
    val group: ItemGroup = ItemGroup(
        NamespacedKey("ballcore", "furnaces"),
        ItemStack(Material.WHITE_CONCRETE),
    )

object Furnace:
    private val tierOneLore =
        txt"Capable of smelting ores with increased efficiency"

    private val ironFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "iron_furnace"),
        Material.FURNACE,
        txt"Iron Furnace",
        tierOneLore,
    )
    private val tinFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "tin_furnace"),
        Material.FURNACE,
        txt"Tin Furnace",
        tierOneLore,
    )
    private val aluminumFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "aluminum_furnace"),
        Material.FURNACE,
        txt"Aluminum Furnace",
        tierOneLore,
    )
    private val zincFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "zinc_furnace"),
        Material.FURNACE,
        txt"Zinc Furnace",
        tierOneLore,
    )

    private val tierOne: List[(CustomItemStack, CustomItemStack)] =
        List(
            (ironFurnace, ItemStacks.iron.ingot),
            (tinFurnace, ItemStacks.tin.ingot),
            (aluminumFurnace, ItemStacks.aluminum.ingot),
            (zincFurnace, ItemStacks.zinc.ingot),
        )

    private val tierTwoLore =
        txt"Capable of smelting ores with astounding efficiency"

    private val entschloseniteFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "entschlossenite_furnace"),
        Material.FURNACE,
        txt"Entschlossenite Furnace",
        tierTwoLore,
    )

    private val tierTwo: List[CustomItemStack] = List(entschloseniteFurnace)

    private val tierThreeLore =
        txt"Capable of smelting ores with supernatural efficiency"

    private val praecantatioFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "praecantatio_furnace"),
        Material.BLAST_FURNACE,
        txt"Praecantatio Furnace",
        tierThreeLore,
    )
    private val auramFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "auram_furnace"),
        Material.BLAST_FURNACE,
        txt"Auram Furnace",
        tierThreeLore,
    )
    private val alkimiaFurnace: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "alkimia_furnace"),
        Material.BLAST_FURNACE,
        txt"Alkimia Furnace",
        tierThreeLore,
    )

    private val tierThree: List[CustomItemStack] =
        List(praecantatioFurnace, auramFurnace, alkimiaFurnace)

    def registerItems()(using
        bm: BlockManager,
        registry: ItemRegistry,
        server: Server,
        plugin: JavaPlugin,
        sql: SQLManager,
    ): Unit =
        server.getPluginManager.registerEvents(FurnaceListener(), plugin)
        val _ = (tierTwo, tierThree)
        List(
            (FurnaceTier.One, tierOne)
        )
            .foreach { (tier, items) =>
                items.foreach { item =>
                    val recipe = ShapedRecipe(
                        NamespacedKey(
                            item._1.itemID.namespace(),
                            item._1.itemID.value(),
                        ),
                        item._1,
                    )
                    recipe.shape(
                        "III",
                        "IFI",
                        "III",
                    )
                    recipe.setIngredient('I', ExactChoice(item._2))
                    recipe.setIngredient('F', MaterialChoice(Material.FURNACE))
                    registry.register(Furnace(tier, item._1))
                    registry.addRecipe(recipe)
                }
            }

class Furnace(furnaceTier: FurnaceTier, items: CustomItemStack)
    extends CustomItem:
    def group: ItemGroup = Furnaces.group

    def template: CustomItemStack = items

    val tier: FurnaceTier = furnaceTier
