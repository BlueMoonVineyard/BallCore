package BallCore.Rest

import BallCore.Storage.SQLManager
import org.bukkit.Tag
import org.bukkit.entity.Item
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.plugin.Plugin

import java.util as ju
import java.util.logging.Logger
import scala.jdk.CollectionConverters.ListHasAsScala

object RestListener:
    /** Calculates rest RNG from a block drop, changing the amount of items
      * dropped depending on the dropped items and rest value.
      *
      * @param items
      *   The items that the block would normally drop.
      * @param rest
      *   The player's rest value.
      * @return
      *   A containing the item(s) that would drop after rest calculations.
      */
    private def applyRestRNG(items: ju.List[Item]): List[Item] =
        items.asScala.toList

class RestListener()(using rm: RestManager, sql: SQLManager, p: Plugin)
    extends Listener:
    import RestListener.*

    val log: Logger = p.getLogger

    /** Processes drops with rest RNG by intercepting the drop and replacing it
      * with our own.
      *
      * @param event
      *   The `BlockDropItemEvent`.
      */
    @EventHandler(priority = EventPriority.NORMAL)
    def onBlockDropItem(event: BlockDropItemEvent): Unit =
        // these logs are meant to be removed once the feature is complete
        log.info("BlockDropItemEvent fired")

        if event.isCancelled then return ()

        event.setCancelled(true)

        val playerId = event.getPlayer.getUniqueId
        val restValue = rm.getPlayerRest(playerId)
        val blockState = event.getBlockState
        val location = blockState.getLocation

        log.info(s"$restValue")

        // Check if the block is applicable for rest calculations
        if Tag.CROPS.isTagged(blockState.getType) then
            log.info("Crop")
            // Manually drop items from the block break
            val items = applyRestRNG(event.getItems)

            for item <- items do
                blockState.getWorld.dropItemNaturally(
                    location,
                    item.getItemStack,
                )

            sql.useBlocking(rm.save(playerId, restValue - 0.01))
