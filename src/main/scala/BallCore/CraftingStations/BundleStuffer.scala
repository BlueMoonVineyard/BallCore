package BallCore.CraftingStations

import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import BallCore.CustomItems.CustomItemStack
import org.bukkit.Material
import BallCore.TextComponents._
import BallCore.CustomItems.Listeners
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.ItemDespawnEvent
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Item
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.inventory.meta.BundleMeta
import org.bukkit.inventory.ItemStack

object BundleStuffer:
    val id = NamespacedKey("ballcore", "bundle_stuffer")
    val template = CustomItemStack.make(
        id,
        Material.FLETCHING_TABLE,
        txt"Bundle Stuffer",
        txt"Right click to place a bundle on top",
        txt"Overstuffs a bundle with items from adjacent chests when an attached button is pressed",
    )

    def isBundleStufferBundle(item: Entity): Boolean =
        if !item.isInstanceOf[Item] then return false

        item.getMetadata("ballcore:is_bundle_stuffer_bundle")
            .asScala
            .exists(_.asBoolean())

class BundleStufferListener extends Listener:
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def preventBundleStufferItemsFromDespawning(event: ItemDespawnEvent): Unit =
        if BundleStuffer.isBundleStufferBundle(event.getEntity()) then
            event.setCancelled(true)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def preventBundleStufferItemsFromBeingPickedUp(
        event: PlayerAttemptPickupItemEvent
    ): Unit =
        if BundleStuffer.isBundleStufferBundle(event.getItem()) then
            event.setCancelled(true)

class BundleStuffer(using p: Plugin)
    extends CustomItem,
      Listeners.BlockClicked,
      Listeners.BlockRemoved,
      Listeners.BlockRedstoneOn:
    override def group: ItemGroup =
        CraftingStations.group
    override def id: NamespacedKey =
        BundleStuffer.id
    override def template: CustomItemStack =
        BundleStuffer.template

    private def findPlacedBundle(of: Block): Option[Item] =
        val around = of.getLocation().add(0.5d, 1.1d, 0.5d)
        around
            .getWorld()
            .getNearbyEntities(
                around,
                0.2,
                0.2,
                0.2,
                BundleStuffer.isBundleStufferBundle,
            )
            .asScala
            .view
            .flatMap {
                case x: Item => Some(x)
                case _ => None
            }
            .headOption

    override def onBlockClicked(event: PlayerInteractEvent): Unit =
        findPlacedBundle(event.getClickedBlock()) match
            case None =>
                if event.getItem() == null || event
                        .getItem()
                        .getType() != Material.BUNDLE
                then return

                val block = event.getClickedBlock()
                val itemSpawnLocation =
                    block.getLocation().add(0.5d, 1.2d, 0.5d)
                val item = itemSpawnLocation
                    .getWorld()
                    .dropItem(
                        itemSpawnLocation,
                        event.getItem(),
                        x => {
                            x.setVelocity(org.bukkit.util.Vector())
                        },
                    )
                item.setMetadata(
                    "ballcore:is_bundle_stuffer_bundle",
                    FixedMetadataValue(p, true),
                )
                event.getPlayer().getInventory().setItemInMainHand(null)
            case Some(bundle) =>
                bundle.removeMetadata("ballcore:is_bundle_stuffer_bundle", p)

    override def onBlockRemoved(event: BlockBreakEvent): Unit =
        findPlacedBundle(event.getBlock()).foreach { bundle =>
            bundle.removeMetadata("ballcore:is_bundle_stuffer_bundle", p)
        }

    private val maximumCapacity = 4096

    private def itemWeight(of: ItemStack): Int =
        (64 / of.getMaxStackSize()) * of.getAmount()

    override def onRedstonePulsed(
        targetBlock: Block,
        event: BlockRedstoneEvent,
    ): Unit =
        for {
            bundle <- findPlacedBundle(targetBlock)
            chest <- WorkChestUtils.findWorkChest(targetBlock)
            (workChest, workInventory) = chest
        } yield
            val bundleStack = bundle.getItemStack()
            val bundleMeta = bundleStack.getItemMeta.asInstanceOf[BundleMeta]
            val count = () =>
                bundleMeta
                    .getItems()
                    .asScala
                    .filterNot(_ == null)
                    .foldLeft(0)((x, s) => x + itemWeight(s))

            workInventory.asScala.filterNot(_ == null).foreach { stack =>
                if count() + itemWeight(stack) <= maximumCapacity then
                    bundleMeta.addItem(stack.clone())
                    stack.setAmount(0)
            }

            bundleStack.setItemMeta(bundleMeta)
            bundle.setItemStack(bundleStack)
        ()
