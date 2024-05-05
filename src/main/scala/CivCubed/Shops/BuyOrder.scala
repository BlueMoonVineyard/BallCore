package CivCubed.Shops

import CivCubed.CustomItems.{CustomItem, CustomItemStack, ItemGroup}
import CivCubed.TextComponents.*
import org.bukkit.{Material, NamespacedKey}

import scala.util.chaining.*

object BuyOrder:
    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("civcubed", "buy_order"),
        Material.PAPER,
        txt"Buy Order",
    )
    template.setItemMeta(
        template.getItemMeta.tap(_.setCustomModelData(Order.buyOrderCMD))
    )

class BuyOrder extends CustomItem:
    def group: ItemGroup = Order.group

    def template: CustomItemStack = BuyOrder.template
