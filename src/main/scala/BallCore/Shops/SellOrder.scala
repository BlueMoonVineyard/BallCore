package BallCore.Shops

import BallCore.CustomItems.*
import BallCore.TextComponents.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.{EquipmentSlot, ItemStack}
import org.bukkit.persistence.{PersistentDataContainer, PersistentDataType}
import org.bukkit.{Material, NamespacedKey}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.chaining.*

def countPayment(
                  of: Iterator[ItemStack],
                  isValidPayment: ItemStack => Boolean
                ): (Int, List[ItemStack]) =
  of.filterNot(_ == null).foldLeft((0, Nil: List[ItemStack])) { (tuple, item) =>
    val (sum, items) = tuple
    if isValidPayment(item) then (sum + item.getAmount, item :: items)
    else (sum, items)
  }

def countReceive(
                  of: Iterator[ItemStack],
                  payment: ItemStack
                ): (Int, List[ItemStack]) =
  of.foldLeft((0, Nil: List[ItemStack])) { (tuple, item) =>
    val (sum, items) = tuple
    if item == null then (sum + payment.getMaxStackSize, item :: items)
    else if item.isSimilar(payment) then
      (sum + (item.getMaxStackSize - item.getAmount), item :: items)
    else (sum, items)
  }

def deduct(from: List[ItemStack], amount: Int): Unit =
  val remaining = from.foldLeft(amount) { (unpaid, item) =>
    if unpaid > 0 then
      if item.getAmount >= unpaid then
        val amount = item.getAmount
        item.setAmount(amount - unpaid)
        unpaid - amount
      else
        val amount = item.getAmount
        item.setAmount(0)
        unpaid - amount
    else unpaid
  }
  assert(remaining <= 0)

object SellOrderDescription:
  def debugEnumerateFrom(
                          inv: Iterator[ItemStack]
                        ): Iterator[SellOrderItemDescription] =
    inv
      .filterNot(_ == null)
      .map(x =>
        SellOrderItemDescription.deserialize(
          x.getItemMeta.getPersistentDataContainer
        )
      )

  def enumerateFrom(inv: Iterator[ItemStack]): Iterator[SellOrderDescription] =
    inv
      .filterNot(_ == null)
      .flatMap(x =>
        SellOrderItemDescription
          .deserialize(x.getItemMeta.getPersistentDataContainer)
          .into()
      )

enum ExchangeError:
  case buyerCantAfford(has: Int, needed: Int)
  case buyerCantReceive
  case sellerCantAfford(has: Int, needed: Int)
  case sellerCantReceive
  case unknownPrice

case class SellOrderDescription(
                                 selling: (ItemStack, Int),
                                 price: (CustomMaterial, Int)
                               ):
  extension (i: ItemStack)
    private def normalized: ItemStack =
      i.clone().tap(_.setAmount(1))

  def countPossibleExchanges(inv: Iterator[ItemStack]): Int =
    val counted = scala.collection.mutable.Map[ItemStack, Int]()
    inv.filterNot(_ == null).foreach { itemStack =>
      val key = itemStack.normalized
      counted(key) = counted.getOrElseUpdate(key, 0) + itemStack.getAmount
    }
    counted.getOrElse(selling._1.normalized, 0) / selling._2

  def perform(
               buyerPay: Iterator[ItemStack],
               buyerReceive: Iterator[ItemStack],
               buyerInsert: ((ItemStack, Int)) => Unit,
               sellerPay: Iterator[ItemStack],
               sellerReceive: Iterator[ItemStack],
               sellerInsert: ((ItemStack, Int)) => Unit
             )(using ItemRegistry): Either[ExchangeError, Unit] =
    val (buyerCount, buyerItems) = countPayment(buyerPay, price._1.matches)
    if buyerCount < price._2 then
      return Left(ExchangeError.buyerCantAfford(buyerCount, price._2))

    val (buyerFreeCount, buyerFreeItems) =
      countReceive(buyerReceive, selling._1)
    if buyerFreeCount < selling._2 then
      return Left(ExchangeError.buyerCantReceive)

    val (sellerCount, sellerItems) =
      countPayment(sellerPay, selling._1.isSimilar)
    if sellerCount < selling._2 then
      return Left(ExchangeError.sellerCantAfford(sellerCount, selling._2))

    val paymentTemplate = price._1.template() match
      case Some(it) => it
      case None => return Left(ExchangeError.unknownPrice)
    val (sellerFreeCount, sellerFreeItems) =
      countReceive(sellerReceive, paymentTemplate)
    if sellerFreeCount < price._2 then
      return Left(ExchangeError.sellerCantReceive)

    // we've confirmed that we can do the deed

    // deduct payment from buyer
    deduct(buyerItems, price._2)

    // deduct product from seller
    deduct(sellerItems, selling._2)

    // insert product into buyer
    buyerInsert(selling)

    // insert product into seller
    sellerInsert((paymentTemplate, price._2))

    Right(())

object SellOrderItemDescription:
  def deserialize(from: PersistentDataContainer): SellOrderItemDescription =
    val selling = Option(
      from.get(
        NamespacedKey("ballcore", "sell_order_selling"),
        PersistentDataType.BYTE_ARRAY
      )
    ).flatMap { it =>
      Try(
        ObjectInputStream(ByteArrayInputStream(it))
          .readObject()
          .asInstanceOf[(Array[Byte], Int)]
      ).toOption
    }.map { (item, count) =>
      (ItemStack.deserializeBytes(item), count)
    }
    val price = Option(
      from.get(
        NamespacedKey("ballcore", "sell_order_price"),
        PersistentDataType.BYTE_ARRAY
      )
    ).flatMap { it =>
      Try(
        ObjectInputStream(ByteArrayInputStream(it))
          .readObject()
          .asInstanceOf[(CustomMaterial, Int)]
      ).toOption
    }
    SellOrderItemDescription(selling, price)

case class SellOrderItemDescription(
                                     selling: Option[(ItemStack, Int)],
                                     price: Option[(CustomMaterial, Int)]
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
      into.set(
        NamespacedKey("ballcore", "sell_order_selling"),
        PersistentDataType.BYTE_ARRAY,
        buf.toByteArray
      )
    }
    price.foreach { (material, count) =>
      val buf = ByteArrayOutputStream()
      val os = ObjectOutputStream(buf)
      os.writeObject((material, count))
      into.set(
        NamespacedKey("ballcore", "sell_order_price"),
        PersistentDataType.BYTE_ARRAY,
        buf.toByteArray
      )
    }

  def apply(to: ItemStack)(using ItemRegistry): Unit =
    val sellingLore: List[Component] =
      selling.toList.flatMap { (is, count) =>
        List(
          txt"Selling".color(NamedTextColor.GOLD),
          txt"  ${is.displayName()} × $count"
        )
      }

    val priceLore: List[Component] =
      price.toList.flatMap { (is, count) =>
        List(
          txt"Price".color(NamedTextColor.GOLD),
          txt"  ${is.displayName()} × $count"
        )
      }

    to.setItemMeta(
      to.getItemMeta.tap(x => serialize(x.getPersistentDataContainer))
    )

    val fullLore =
      sellingLore ++
        (if sellingLore.isEmpty then Nil
        else List(txt"")) ++
        priceLore ++
        (if priceLore.isEmpty then Nil
        else List(txt"")) ++
        SellOrder.defaultLore

    to.lore(fullLore.map(CustomItemStack.loreify).asJava)

object SellOrder:
  val piss = s""
  val defaultLore: List[Component] = List(
    txt"${keybind("key.use")}".color(NamedTextColor.BLUE),
    txt"  Sets the sell order's result to the item in your offhand",
    txt"",
    txt"${keybind("key.use")} while sneaking".color(NamedTextColor.BLUE),
    txt"  Sets the sell order's price to the item in your offhand"
  )
  val template: CustomItemStack = CustomItemStack.make(
    NamespacedKey("ballcore", "sell_order"),
    Material.PAPER,
    txt"Sell Order",
    defaultLore: _*
  )
  template.setItemMeta(
    template.getItemMeta.tap(_.setCustomModelData(Order.sellOrderCMD))
  )

class SellOrder(using registry: ItemRegistry)
  extends CustomItem,
    Listeners.ItemUsed:
  def group: ItemGroup = Order.group

  def template: CustomItemStack = SellOrder.template

  override def onItemUsed(event: PlayerInteractEvent): Unit =
    if event.getHand != EquipmentSlot.HAND then return

    val player = event.getPlayer
    val offhand = player.getInventory.getItemInOffHand
    val order = player.getInventory.getItemInMainHand

    val orderDescription = SellOrderItemDescription.deserialize(
      order.getItemMeta.getPersistentDataContainer
    )

    if player.isSneaking then
      val kind = Option(
        registry
          .lookup(offhand)
          .map(_.id.toString())
          .map(CustomMaterial.custom.apply)
          .getOrElse(CustomMaterial.vanilla(offhand.getType))
      )
        .filterNot(_ == CustomMaterial.vanilla(Material.AIR))
        .map { kind =>
          (kind, offhand.getAmount)
        }

      orderDescription.copy(price = kind).apply(order)
      kind match
        case None =>
          player.sendActionBar(txt"Sell order price cleared")
        case Some(_) =>
          player.sendActionBar(txt"Sell order price set")
    else
      val kind = Option(offhand)
        .filterNot(_.getType == CustomMaterial.vanilla(Material.AIR))
        .map { itemStack =>
          (itemStack, offhand.getAmount)
        }

      orderDescription.copy(selling = kind).apply(order)

      if offhand.getType == Material.AIR then
        player.sendActionBar(txt"Sell order result cleared")
      else player.sendActionBar(txt"Sell order result set")
