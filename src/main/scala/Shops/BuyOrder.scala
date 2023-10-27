package BallCore.Shops

import org.bukkit.NamespacedKey
import org.bukkit.Material
import BallCore.TextComponents._
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.CustomItemStack
import scala.util.chaining._

object BuyOrder:
    val template = CustomItemStack.make(NamespacedKey("ballcore", "buy_order"), Material.PAPER, txt"Buy Order")
    template.setItemMeta(template.getItemMeta().tap(_.setCustomModelData(Order.buyOrderCMD)))

class BuyOrder extends CustomItem:
    def group = Order.group
    def template = BuyOrder.template
