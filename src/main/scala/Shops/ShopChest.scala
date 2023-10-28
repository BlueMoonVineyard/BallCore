package BallCore.Shops

import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.Listeners
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Material
import scala.util.chaining._
import org.bukkit.event.player.PlayerInteractEvent
import scala.collection.concurrent.TrieMap
import org.bukkit.block.DoubleChest
import org.bukkit.block.Chest
import java.util.UUID
import BallCore.TextComponents._
import BallCore.CustomItems.ItemRegistry

object ShopChest:
    val template = CustomItemStack.make(NamespacedKey("ballcore", "shop_chest"), Material.CHEST, txt"Shop Chest", txt"Can be stocked with sell orders and items to sell goods")
    template.setItemMeta(template.getItemMeta().tap(_.setCustomModelData(Order.sellOrderCMD)))

class ShopChest(using ItemRegistry) extends CustomItem, Listeners.BlockLeftClicked:
    def group = Order.group
    def template = ShopChest.template

    case class PurchasingState(
        val index: Int,
    )

    val states = TrieMap[UUID, PurchasingState]()

    override def onBlockLeftClicked(event: PlayerInteractEvent): Unit =
        val inv = event.getClickedBlock().getState() match
            case double: DoubleChest => double.getInventory()
            case single: Chest => single.getInventory()
            case _ => return

        val orders = SellOrderDescription.enumerateFrom(inv.getStorageContents().iterator).toList
        var state = states.getOrElseUpdate(event.getPlayer().getUniqueId(), PurchasingState(-1))
        state = state.copy(index = if orders.isEmpty then -1 else (state.index + 1) % orders.length)
        states(event.getPlayer().getUniqueId()) = state

        val player = event.getPlayer()
        orders.lift(state.index) match
            case None =>
                player.sendServerMessage(txt"This shop chest has no orders.")
            case Some(order) =>
                player.sendServerMessage(txt"")
                player.sendServerMessage(txt"==== Shop Chest ====")
                player.sendServerMessage(txt"Item ${state.index + 1} of ${orders.length}:")
                player.sendServerMessage(txt"Item: ${order.selling._1.displayName().hoverEvent(order.selling._1)} × ${order.selling._2}")
                player.sendServerMessage(txt"Price: ${order.price._1.displayName()} × ${order.price._2}")
                player.sendServerMessage(txt"")
                player.sendServerMessage(txt"Punch the chest with ${order.price._1.displayName()} to buy")
