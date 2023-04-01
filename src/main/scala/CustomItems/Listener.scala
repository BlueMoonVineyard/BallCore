package BallCore.CustomItems

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.plugin.Plugin

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
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item.template)
            case _ =>
        
        
