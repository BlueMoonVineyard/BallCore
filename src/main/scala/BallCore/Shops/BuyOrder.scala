package BallCore.Shops

import BallCore.CustomItems.{CustomItem, CustomItemStack, ItemGroup}
import BallCore.TextComponents.*
import org.bukkit.{Material, NamespacedKey}

import scala.util.chaining.*

object BuyOrder:
  val template: CustomItemStack = CustomItemStack.make(
    NamespacedKey("ballcore", "buy_order"),
    Material.PAPER,
    txt"Buy Order"
  )
  template.setItemMeta(
    template.getItemMeta.tap(_.setCustomModelData(Order.buyOrderCMD))
  )

class BuyOrder extends CustomItem:
  def group: ItemGroup = Order.group

  def template: CustomItemStack = BuyOrder.template
