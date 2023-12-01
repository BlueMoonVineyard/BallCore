package BallCore.Shops

import BallCore.CustomItems.*
import BallCore.TextComponents.*
import org.bukkit.block.{Chest, DoubleChest}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.{Inventory, ItemStack}
import org.bukkit.{Material, NamespacedKey}

import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.util.chaining.*

object ShopChest:
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "shop_chest"),
        Material.CHEST,
        txt"Shop Chest",
        txt"Can be stocked with sell orders and items to sell goods",
    )
    template.setItemMeta(
        template.getItemMeta.tap(_.setCustomModelData(Order.sellOrderCMD))
    )

class ShopChest(using ItemRegistry)
    extends CustomItem,
      Listeners.BlockLeftClicked:
    def group: ItemGroup = Order.group

    def template: CustomItemStack = ShopChest.template

    case class PurchasingState(
        index: Int
    )

    val states: TrieMap[UUID, PurchasingState] =
        TrieMap[UUID, PurchasingState]()

    private def addify(inv: Inventory)(toAdd: (ItemStack, Int)): Unit =
        val (item, amount) = toAdd
        val stacks =
            item.clone().tap(_.setAmount(amount % item.getMaxStackSize)) :: List
                .fill(amount / item.getMaxStackSize)(
                    item.clone().tap(_.setAmount(item.getMaxStackSize))
                )
        val res = inv.addItem(stacks: _*)
        assert(res.isEmpty)

    override def onBlockLeftClicked(event: PlayerInteractEvent): Unit =
        val inv = event.getClickedBlock.getState() match
            case double: DoubleChest => double.getInventory
            case single: Chest => single.getInventory
            case _ =>
                return ()

        if event.getItem == null || event.getItem.getType.isAir() then
            val orders = SellOrderDescription
                .enumerateFrom(inv.getStorageContents.iterator)
                .toList
            var state = states.getOrElseUpdate(
                event.getPlayer.getUniqueId,
                PurchasingState(-1),
            )
            state = state.copy(index =
                if orders.isEmpty then -1 else (state.index + 1) % orders.length
            )
            states(event.getPlayer.getUniqueId) = state

            val player = event.getPlayer
            orders.lift(state.index) match
                case None =>
                    player.sendServerMessage(
                        txt"This shop chest has no orders."
                    )
                case Some(order) =>
                    val possibleExchanges =
                        order.countPossibleExchanges(
                            inv.getStorageContents.iterator
                        )
                    player.sendServerMessage(txt"")
                    player.sendServerMessage(txt"==== Shop Chest ====")
                    player.sendServerMessage(
                        txt"Item ${state.index + 1} of ${orders.length}:"
                    )
                    player.sendServerMessage(
                        txt"Item: ${order.selling._1.displayName().hoverEvent(order.selling._1)} × ${order.selling._2} ($possibleExchanges sales available)"
                    )
                    player.sendServerMessage(
                        txt"Price: ${order.price._1.displayName()} × ${order.price._2}"
                    )
                    player.sendServerMessage(txt"")
                    player.sendServerMessage(
                        txt"Punch the chest with ${order.price._1.displayName()} to buy"
                    )
        else
            val orders = SellOrderDescription
                .enumerateFrom(inv.getStorageContents.iterator)
                .toList
            val state = states.getOrElseUpdate(
                event.getPlayer.getUniqueId,
                PurchasingState(-1),
            )
            val player = event.getPlayer

            orders.lift(state.index) match
                case Some(order) if order.price._1.matches(event.getItem) =>
                    val playerInventory = event.getPlayer.getInventory
                    order.perform(
                        playerInventory.getStorageContents.iterator,
                        playerInventory.getStorageContents.iterator,
                        addify(playerInventory),
                        inv.getStorageContents.iterator,
                        inv.getStorageContents.iterator,
                        addify(inv),
                    ) match
                        case Left(err) =>
                            err match
                                case ExchangeError.buyerCantAfford(
                                        has,
                                        needed,
                                    ) =>
                                    player.sendServerMessage(
                                        txt"You can't afford that purchase (it requires $needed ${order.price._1
                                                .displayName()} but you only have $has)"
                                    )
                                case ExchangeError.buyerCantReceive =>
                                    player.sendServerMessage(
                                        txt"You can't store the purchased goods in your inventory"
                                    )
                                case ExchangeError.sellerCantAfford(
                                        has,
                                        needed,
                                    ) =>
                                    player.sendServerMessage(
                                        txt"The chest doesn't have enough items to sell (it requires $needed ${order.selling._1
                                                .displayName()} but it only has $has)"
                                    )
                                case ExchangeError.sellerCantReceive =>
                                    player.sendServerMessage(
                                        txt"The chest doesn't have enough space to store the payment"
                                    )
                                case ExchangeError.unknownPrice =>
                                    player.sendServerMessage(
                                        txt"The price is invalid"
                                    )

                        case Right(_) =>
                            player.sendServerMessage(
                                txt"You paid ${order.price._1.displayName()} × ${order.price._2} and received ${order.selling._1
                                        .displayName()
                                        .hoverEvent(order.selling._1)} × ${order.selling._2} "
                            )
                case Some(order) =>
                    player.sendServerMessage(
                        txt"Invalid payment, expected ${order.price._2}"
                    )
                case _ =>
                    ()
