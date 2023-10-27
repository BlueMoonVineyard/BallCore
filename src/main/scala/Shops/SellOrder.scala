package BallCore.Shops

import org.bukkit.NamespacedKey
import org.bukkit.Material
import BallCore.TextComponents._
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.CustomItemStack
import scala.util.chaining._
import org.bukkit.inventory.ItemStack
import BallCore.CustomItems.CustomMaterial
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import scala.util.Try
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream
import net.kyori.adventure.text.format.NamedTextColor
import BallCore.CustomItems.Listeners
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import net.kyori.adventure.text.Component
import scala.jdk.CollectionConverters._
import BallCore.CustomItems.ItemRegistry

case class SellOrderDescription(
    val selling: (ItemStack, Int),
    val price: (CustomMaterial, Int),
)

object SellOrderItemDescription:
    def deserialize(from: PersistentDataContainer): SellOrderItemDescription =
        val selling = Option(from.get(NamespacedKey("ballcore", "sell_order_selling"), PersistentDataType.BYTE_ARRAY)).flatMap { it =>
            Try(ObjectInputStream(ByteArrayInputStream(it)).readObject().asInstanceOf[(Array[Byte], Int)]).toOption
        }.map { (item, count) =>
            (ItemStack.deserializeBytes(item), count)
        }
        val price = Option(from.get(NamespacedKey("ballcore", "sell_order_price"), PersistentDataType.BYTE_ARRAY)).flatMap { it =>
            Try(ObjectInputStream(ByteArrayInputStream(it)).readObject().asInstanceOf[(CustomMaterial, Int)]).toOption
        }
        SellOrderItemDescription(selling, price)

case class SellOrderItemDescription(
    val selling: Option[(ItemStack, Int)],
    val price: Option[(CustomMaterial, Int)],
):
    def into(): Option[SellOrderDescription] =
        for {
            sellingP <- selling
            priceP <- price
        } yield SellOrderDescription(sellingP, priceP)
    def serialize(into: PersistentDataContainer): Unit =
        selling.foreach { (item, count) =>
            val buf = ByteArrayOutputStream()
            val os = ObjectOutputStream(buf)
            os.writeObject((item.serializeAsBytes(), count))
            into.set(NamespacedKey("ballcore", "sell_order_selling"), PersistentDataType.BYTE_ARRAY, buf.toByteArray())
        }
        price.foreach { (material, count) =>
            val buf = ByteArrayOutputStream()
            val os = ObjectOutputStream(buf)
            os.writeObject((material, count))
            into.set(NamespacedKey("ballcore", "sell_order_price"), PersistentDataType.BYTE_ARRAY, buf.toByteArray())
        }

    def apply(to: ItemStack)(using ItemRegistry): Unit =
        val sellingLore: List[Component] =
            selling.toList.flatMap { (is, count) =>
                List(
                    txt"Selling".color(NamedTextColor.GOLD),
                    txt"  ${is.displayName()} × ${count}",
                )
            }

        val priceLore: List[Component] =
            price.toList.flatMap { (is, count) =>
                List(
                    txt"Price".color(NamedTextColor.GOLD),
                    txt"  ${is.displayName()} × ${count}",
                )
            }

        to.setItemMeta(to.getItemMeta().tap(x => serialize(x.getPersistentDataContainer())))

        val fullLore =
            sellingLore ++
            (if sellingLore.isEmpty then
                Nil
            else
                List(txt"")) ++
            priceLore ++
            (if priceLore.isEmpty then
                Nil
            else
                List(txt"")) ++
            SellOrder.defaultLore

        to.lore(fullLore.map(CustomItemStack.loreify).asJava)

object SellOrder:
    val piss = s""
    val defaultLore = List(
        txt"${keybind("key.use")}".color(NamedTextColor.BLUE),
        txt"  Sets the sell order's result to the item in your offhand",
        txt"",
        txt"${keybind("key.use")} while sneaking".color(NamedTextColor.BLUE),
        txt"  Sets the sell order's price to the item in your offhand",
    )
    val template = CustomItemStack.make(NamespacedKey("ballcore", "sell_order"), Material.PAPER, txt"Sell Order", defaultLore: _*)
    template.setItemMeta(template.getItemMeta().tap(_.setCustomModelData(Order.sellOrderCMD)))

class SellOrder(using registry: ItemRegistry) extends CustomItem, Listeners.ItemUsed:
    def group = Order.group
    def template = SellOrder.template

    override def onItemUsed(event: PlayerInteractEvent): Unit =
        if event.getHand() != EquipmentSlot.HAND then
            return

        val player = event.getPlayer()
        val offhand = player.getInventory().getItemInOffHand()
        val order = player.getInventory().getItemInMainHand()

        val orderDescription = SellOrderItemDescription.deserialize(order.getItemMeta().getPersistentDataContainer())

        if player.isSneaking() then
            val kind = Option(registry.lookup(offhand).map(_.id.toString()).map(CustomMaterial.custom.apply).getOrElse(CustomMaterial.vanilla(offhand.getType())))
                .filterNot(_ == CustomMaterial.vanilla(Material.AIR))
                .map { kind =>
                    (kind, offhand.getAmount())
                }

            orderDescription.copy(price = kind).apply(order)
            kind match
                case None =>
                    player.sendActionBar(txt"Sell order price cleared")
                case Some(_) =>
                    player.sendActionBar(txt"Sell order price set")
        else
            val kind = Option(offhand)
                .filterNot(_.getType() == CustomMaterial.vanilla(Material.AIR))
                .map { itemStack =>
                    (itemStack, offhand.getAmount())
                }

            orderDescription.copy(selling = kind).apply(order)

            if offhand.getType() == Material.AIR then
                player.sendActionBar(txt"Sell order result cleared")
            else
                player.sendActionBar(txt"Sell order result set")
