package CivCubed.PVPServer

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.entity.EntityType
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.plugin.Plugin
import org.bukkit.entity.ItemFrame
import org.bukkit.event.player.PlayerInteractEntityEvent

object ItemFrameDispenser:
    val pdcKey = NamespacedKey("civcubed", "item_frame_is_dispenser")
    val node =
        CommandTree("item-frame-make")
            .withRequirement(_.hasPermission("civcubed.cheat"))
            .executesPlayer({ (sender, args) =>
                val result = sender.rayTraceBlocks(20)
                val block = result.getHitBlock()
                val face = result.getHitBlockFace()

                val loc = block.getRelative(face).getLocation
                loc.setDirection(face.getDirection)

                val world = block.getWorld()

                val itemFrame = world.spawnEntity(loc, EntityType.ITEM_FRAME).asInstanceOf[ItemFrame]
                itemFrame.setItem(sender.getEquipment.getItemInMainHand)
                itemFrame.setVisible(false)

                val pdc = itemFrame.getPersistentDataContainer
                pdc.set(pdcKey, PersistentDataType.BOOLEAN, true)
            }: PlayerCommandExecutor)

    def register()(using p: Plugin) =
        node.register()
        p.getServer.getPluginManager.registerEvents(ItemFrameDispenser(), p)

class ItemFrameDispenser extends Listener:
    @EventHandler
    def interact(pie: PlayerInteractEntityEvent): Unit =
        val entity = pie.getRightClicked
        val isDispenser = entity.getPersistentDataContainer.getOrDefault(ItemFrameDispenser.pdcKey, PersistentDataType.BOOLEAN, false)

        entity match
            case itemFrame: ItemFrame if isDispenser =>
                val targetEquipment = pie.getPlayer.getEquipment
                val item = itemFrame.getItem
                if pie.getPlayer.isSneaking then
                    item.setAmount(item.getMaxStackSize)

                if isNothing(targetEquipment.getItemInMainHand) then
                    targetEquipment.setItemInMainHand(item)
                else
                    pie.getPlayer.getInventory.addItem(item).forEach { (amt, is) =>
                        val _ = pie.getPlayer.getWorld
                            .dropItemNaturally(pie.getPlayer.getLocation, is)
                    }

                pie.setCancelled(true)
            case _ =>
