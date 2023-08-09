package BallCore.CustomItems

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.plugin.Plugin
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot

object CustomItemListener:
    def register()(using bm: BlockManager, reg: ItemRegistry, plugin: Plugin): Unit =
        plugin.getServer().getPluginManager().registerEvents(new CustomItemListener, plugin)

class CustomItemListener(using bm: BlockManager, reg: ItemRegistry) extends org.bukkit.event.Listener:
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        reg.lookup(event.getItemInHand()) match
            case Some(item) =>
                val cancelled =
                    item match
                        case place: Listeners.BlockPlaced =>
                            place.onBlockPlace(event)
                            event.isCancelled()
                        case _ => false
                if cancelled then
                    return
                bm.setCustomItem(event.getBlockPlaced(), item)
            case _ => ()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onBlockBreak(event: BlockBreakEvent): Unit =
        bm.getCustomItem(event.getBlock()) match
            case Some(item) =>
                val cancelled =
                    item match
                        case break: Listeners.BlockRemoved =>
                            break.onBlockRemoved(event)
                            event.isCancelled()
                        case _ => false
                if cancelled then
                    return
                bm.clearCustomItem(event.getBlock())
                event.setDropItems(false)
                val _ = event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item.template)
            case _ =>

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onInteractItem(event: PlayerInteractEvent): Unit =
        if !event.hasItem() || event.getAction() != Action.RIGHT_CLICK_BLOCK then
            return

        reg.lookup(event.getItem()) match
            case Some(item) =>
                val cancel =
                    item match
                        case click: Listeners.ItemUsedOnBlock =>
                            click.onItemUsed(event)
                            true
                        case _ => false
                event.setCancelled(cancel)
            case None =>

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onInteractBlock(event: PlayerInteractEvent): Unit =
        if event.getHand() != EquipmentSlot.HAND then
            return
        if !event.hasBlock() || event.getAction() != Action.RIGHT_CLICK_BLOCK then
            return

        bm.getCustomItem(event.getClickedBlock()) match
            case Some(item) =>
                val cancel =
                    item match
                        case click: Listeners.BlockClicked =>
                            click.onBlockClicked(event)
                            true
                        case _ =>
                            false
                event.setCancelled(cancel)
            case _ =>
