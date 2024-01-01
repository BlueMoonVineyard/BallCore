// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import BallCore.Storage.SQLManager
import org.bukkit.block.Chest
import org.bukkit.event.block.{Action, BlockBreakEvent, BlockPlaceEvent}
import org.bukkit.event.player.{PlayerInteractEvent, PlayerJoinEvent}
import org.bukkit.event.{EventHandler, EventPriority}
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin

import scala.jdk.CollectionConverters.*
import org.bukkit.event.Event.Result
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.Tag
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.block.data.`type`.Switch
import org.bukkit.Keyed

object CustomItemListener:
    def register()(using
        bm: BlockManager,
        reg: ItemRegistry,
        plugin: Plugin,
        sql: SQLManager,
    ): Unit =
        plugin.getServer.getPluginManager
            .registerEvents(new CustomItemListener, plugin)

class CustomItemListener(using
    bm: BlockManager,
    reg: ItemRegistry,
    sql: SQLManager,
) extends org.bukkit.event.Listener:
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onPlayerJoin(event: PlayerJoinEvent): Unit =
        event.getPlayer.discoverRecipes(reg.recipes().asJava)
        ()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        reg.lookup(event.getItemInHand) match
            case Some(item) if item.template.getType().isBlock() =>
                val cancelled =
                    item match
                        case place: Listeners.BlockPlaced =>
                            place.onBlockPlace(event)
                            event.isCancelled
                        case _ => false

                if cancelled then return
                sql.useBlocking(
                    sql.withS(bm.setCustomItem(event.getBlockPlaced, item))
                )
            case _ => ()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onRedstoneBlock(event: BlockRedstoneEvent): Unit =
        event.getBlock.getBlockData match
            case e: Switch =>
                val targetBlock = event
                    .getBlock()
                    .getRelative(e.getFacing().getOppositeFace())
                sql.useBlocking(
                    sql.withS(bm.getCustomItem(targetBlock))
                ) match
                    case Some(item) =>
                        item match
                            case redstone: Listeners.BlockRedstoneOn =>
                                redstone.onRedstonePulsed(targetBlock, event)
                            case _ =>
                    case _ =>
            case _ =>

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onBlockBreak(event: BlockBreakEvent): Unit =
        sql.useBlocking(sql.withS(bm.getCustomItem(event.getBlock))) match
            case Some(item) =>
                val cancelled =
                    item match
                        case break: Listeners.BlockRemoved =>
                            break.onBlockRemoved(event)
                            event.isCancelled
                        case _ => false

                if cancelled then return
                sql.useBlocking(sql.withS(bm.clearCustomItem(event.getBlock)))
                event.setDropItems(false)
                event.getBlock.getState() match
                    case it: Chest =>
                        it.getInventory.getStorageContents.view
                            .filterNot(_ == null)
                            .foreach { x =>
                                val _ = event.getBlock.getWorld
                                    .dropItemNaturally(
                                        event.getBlock.getLocation(),
                                        x,
                                    )
                            }
                    case _ => ()
                val _ = event.getBlock.getWorld
                    .dropItemNaturally(
                        event.getBlock.getLocation(),
                        item.template,
                    )
            case _ =>

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onBlockBreakWithItem(event: BlockBreakEvent): Unit =
        val item = event.getPlayer.getInventory.getItemInMainHand
        reg.lookup(item) match
            case Some(item: Listeners.ItemBrokeBlock) =>
                item.onItemBrokeBlock(event)
            case _ =>

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onInteractItemBlock(event: PlayerInteractEvent): Unit =
        if !event.hasItem then return ()

        (reg.lookup(event.getItem), event.getAction) match
            case (
                    Some(click: Listeners.ItemUsedOnBlock),
                    Action.RIGHT_CLICK_BLOCK,
                ) =>
                event.setCancelled(true)
                click.onItemUsedOnBlock(event)
            case (
                    Some(click: Listeners.ItemLeftUsedOnBlock),
                    Action.LEFT_CLICK_BLOCK,
                ) =>
                event.setCancelled(true)
                click.onItemLeftUsedOnBlock(event)
            case _ =>

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    def onInteractItem(event: PlayerInteractEvent): Unit =
        if !event.hasItem || !(event.getAction == Action.RIGHT_CLICK_AIR || (event.getAction == Action.RIGHT_CLICK_BLOCK && event
                .useItemInHand() != Result.DENY))
        then return

        reg.lookup(event.getItem) match
            case Some(item) =>
                val cancel =
                    item match
                        case click: Listeners.ItemUsed =>
                            click.onItemUsed(event)
                            true
                        case _ => false
                event.setCancelled(cancel)
            case None =>

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def blockToolTogetherCrafting(event: PrepareItemCraftEvent): Unit =
        val isCraftingToolsTogether =
            event
                .getInventory()
                .getMatrix()
                .filterNot(_ == null)
                .count(x => Tag.ITEMS_TOOLS.isTagged(x.getType())) >= 2
        if isCraftingToolsTogether then event.getInventory().setResult(null)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def blockUnpermittedVanillaUses(event: PrepareItemCraftEvent): Unit =
        val recipe = event.getRecipe()
        if recipe == null || !recipe.isInstanceOf[Keyed] then return
        val key = recipe.asInstanceOf[Keyed].getKey()
        if key.getNamespace() != "minecraft" then return
        val containsCustomItem = event
            .getInventory()
            .getMatrix()
            .filterNot(_ == null)
            .exists(reg.lookup(_).isDefined)
        if containsCustomItem && !AllowedVanillaRecipes.items.contains(
                recipe.getResult().getType()
            )
        then event.getInventory().setResult(null)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onInteractBlock(event: PlayerInteractEvent): Unit =
        if event.getHand != EquipmentSlot.HAND then return
        if !event.hasBlock then return
        if event.getPlayer.isSneaking then return

        sql.useBlocking(
            sql.withS(bm.getCustomItem(event.getClickedBlock))
        ) match
            case Some(item) =>
                val cancel =
                    item match
                        case click: Listeners.BlockClicked
                            if event.getAction == Action.RIGHT_CLICK_BLOCK =>
                            click.onBlockClicked(event)
                            true
                        case click: Listeners.BlockLeftClicked
                            if event.getAction == Action.LEFT_CLICK_BLOCK =>
                            click.onBlockLeftClicked(event)
                            true
                        case _ =>
                            false
                event.setCancelled(cancel)
            case _ =>
