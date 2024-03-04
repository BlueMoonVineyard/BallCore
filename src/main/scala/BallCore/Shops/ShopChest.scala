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
import BallCore.Advancements.UseShopChest

object ShopChest:
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "shop_chest"),
        Material.CHEST,
        trans"items.shop-chest",
        trans"items.shop-chest.lore",
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
                        trans"notifications.shop-chest-has-no-orders"
                    )
                case Some(order) =>
                    val possibleExchanges =
                        order.countPossibleExchanges(
                            inv.getStorageContents.iterator
                        )
                    player.sendServerMessage(txt"")
                    player.sendServerMessage(trans"notifications.shop-chest-title")
                    player.sendServerMessage(
                        trans"notifications.shop-chest.item-page".args((state.index + 1).toComponent, orders.length.toComponent)
                    )
                    player.sendServerMessage(
                        trans"notifications.shop-chest.item".args(order.selling._1.displayName().hoverEvent(order.selling._1), order.selling._2.toComponent, possibleExchanges.toComponent)
                    )
                    player.sendServerMessage(
                        trans"notifications.shop-chest.price".args(order.price._1.displayName(), order.price._2.toComponent)
                    )
                    player.sendServerMessage(txt"")
                    player.sendServerMessage(
                        trans"notifications.shop-chest.punch-to-buy".args(order.price._1.displayName())
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
                                        trans"notifications.shop-chest.buyer-cant-afford".args(needed.toComponent, order.price._1.displayName(), has.toComponent)
                                    )
                                case ExchangeError.buyerCantReceive =>
                                    player.sendServerMessage(
                                        trans"notifications.shop-chest.buyer-cant-receive"
                                    )
                                case ExchangeError.sellerCantAfford(
                                        has,
                                        needed,
                                    ) =>
                                    player.sendServerMessage(
                                        trans"notifications.shop-chest.seller-cant-afford".args(needed.toComponent, order.selling._1.displayName(), has.toComponent)
                                    )
                                case ExchangeError.sellerCantReceive =>
                                    player.sendServerMessage(
                                        trans"notifications.shop-chest.seller-cant-receive"
                                    )
                                case ExchangeError.unknownPrice =>
                                    player.sendServerMessage(
                                        trans"notifications.shop-chest.invalid-price"
                                    )

                        case Right(_) =>
                            UseShopChest.grant(player, "did_exchange")
                            player.sendServerMessage(
                                trans"notifications.shop-chest.paid-received".args(order.price._1.displayName(), order.price._2.toComponent, order.selling._1.displayName().hoverEvent(order.selling._1), order.selling._2.toComponent)
                            )
                case Some(order) =>
                    player.sendServerMessage(
                        trans"notifications.shop-chest.invalid-payment".args(order.price._2.toComponent)
                    )
                case _ =>
                    ()
