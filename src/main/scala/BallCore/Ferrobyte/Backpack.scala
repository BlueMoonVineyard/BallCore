package BallCore.Ferrobyte

import BallCore.CustomItems.CustomItemStack
import BallCore.TextComponents._
import org.bukkit.NamespacedKey
import org.bukkit.Material
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.Listeners
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID
import net.kyori.adventure.text.format.NamedTextColor
import scala.jdk.CollectionConverters._
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextDecoration.State
import BallCore.Storage.SQLManager
import BallCore.Storage.KeyVal
import org.bukkit.inventory.Inventory
import org.bukkit.Bukkit
import io.circe.Encoder
import io.circe.Decoder
import org.bukkit.inventory.InventoryHolder
import BallCore.Folia.EntityExecutionContext
import org.bukkit.plugin.Plugin
import cats.effect.IO
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.Tag
import org.bukkit.event.inventory.InventoryType
import io.circe.Json
import io.circe.HCursor
import io.circe.Decoder.Result
import scala.util.chaining._

object Backpack:
    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "backpack"),
        Material.PAPER,
        txt"Unopened Backpack",
        txt"Stores 18 items",
        txt"${keybind("key.use")} to assign a unique ID and turn it into a usable backpack",
    )
    template.setItemMeta(template.getItemMeta().tap(_.setCustomModelData(7)))
    val idKey = NamespacedKey("ballcore", "backpack_id")

class BackpackHolder(val uuid: UUID, val backpack: Backpack)
    extends InventoryHolder:
    override def getInventory(): Inventory = null

class BackpackListener extends Listener:
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onClose(event: InventoryCloseEvent): Unit =
        event.getInventory.getHolder match
            case it: BackpackHolder =>
                it.backpack.save(event.getInventory, it)
            case _ => ()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onDrop(event: PlayerDropItemEvent): Unit =
        event.getPlayer.getOpenInventory.getTopInventory.getHolder match
            case it: BackpackHolder =>
                if Some(it.uuid) == it.backpack.keyOfOptional(
                        event.getItemDrop.getItemStack
                    )
                then event.setCancelled(true)
            case _ => ()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onClick(event: InventoryClickEvent): Unit =
        val openInventory = event.getWhoClicked.getOpenInventory.getTopInventory
        openInventory.getHolder match
            case it: BackpackHolder =>
                event.getClick match
                    case ClickType.NUMBER_KEY
                        if event.getClickedInventory.getType != InventoryType.PLAYER =>
                        val hotbar = event.getWhoClicked.getInventory.getItem(
                            event.getHotbarButton
                        )
                        if !it.backpack.permissible(hotbar) then
                            event.setCancelled(true)
                    case ClickType.SWAP_OFFHAND
                        if event.getClickedInventory.getType != InventoryType.PLAYER =>
                        val offhand =
                            event.getWhoClicked.getInventory.getItemInOffHand
                        if !it.backpack.permissible(offhand) then
                            event.setCancelled(true)
                    case _ if !it.backpack.permissible(event.getCurrentItem) =>
                        event.setCancelled(true)
                    case _ => ()
            case _ => ()

class Backpack(using
    sql: SQLManager,
    kv: KeyVal,
    p: Plugin,
) extends CustomItem,
      Listeners.ItemUsed:
    override def group: ItemGroup = Ferrobyte.group
    override def template: CustomItemStack = Backpack.template

    override def onItemUsed(event: PlayerInteractEvent): Unit =
        val item = event.getItem
        val meta = item.getItemMeta
        val pdc = meta.getPersistentDataContainer
        if pdc.has(Backpack.idKey, PersistentDataType.STRING) then
            itemUsedOpened(event)
        else itemUsedUnopened(event)

    private[Ferrobyte] def keyOfOptional(item: ItemStack): Option[UUID] =
        Option(item)
            .flatMap(x =>
                Option(
                    x.getItemMeta.getPersistentDataContainer
                        .get(Backpack.idKey, PersistentDataType.STRING)
                )
            )
            .map(UUID.fromString)
    private def newItemMetaFor(it: ItemStack): ItemMeta =
        val meta = it.getItemMeta()
        meta.displayName(
            txt"Backpack"
                .decoration(TextDecoration.ITALIC, State.FALSE)
                .color(NamedTextColor.WHITE)
        )
        val uuid = UUID.randomUUID()
        val uuidTxt = txt(uuid.toString).color(NamedTextColor.WHITE)
        meta.getPersistentDataContainer.set(
            Backpack.idKey,
            PersistentDataType.STRING,
            uuid.toString,
        )
        meta.lore(
            List(
                txt"Stores 18 items",
                txt"",
                txt"ID: ${uuidTxt}",
            ).map(CustomItemStack.loreify).asJava
        )
        meta

    given Encoder[ItemStack] =
        new Encoder[ItemStack]:
            import io.circe.syntax._
            override def apply(a: ItemStack): Json =
                Option(a).map(_.serializeAsBytes()).asJson
    given Decoder[ItemStack] =
        new Decoder[ItemStack]:
            override def apply(c: HCursor): Result[ItemStack] =
                c.as[Option[Array[Byte]]]
                    .map(_.map(ItemStack.deserializeBytes).getOrElse(null))

    private val forbidden =
        Tag.SHULKER_BOXES.getValues().asScala.toSet.union(Set(Material.BUNDLE))

    private[Ferrobyte] def permissible(is: ItemStack): Boolean =
        if is == null then true
        else if forbidden.contains(is.getType) then false
        else if keyOfOptional(is).isDefined then false
        else true

    private def keyOf(is: ItemStack): UUID =
        UUID.fromString(
            is.getItemMeta.getPersistentDataContainer
                .get(Backpack.idKey, PersistentDataType.STRING)
        )

    private[Ferrobyte] def save(inv: Inventory, holder: BackpackHolder): Unit =
        sql.useFireAndForget(
            for _ <- sql.withS(
                    kv.set("backpacks", holder.uuid.toString, inv.getContents)
                )
            yield ()
        )

    private def itemUsedOpened(event: PlayerInteractEvent): Unit =
        val id = keyOf(event.getItem)
        sql.useFireAndForget(for
            items <- sql.withS(
                kv.get[Array[ItemStack]]("backpacks", id.toString)
            )
            inv =
                val inv = Bukkit.createInventory(
                    BackpackHolder(id, this),
                    9 * 2,
                    txt"Backpack",
                )
                items.foreach(inv.setContents)
                inv
            _ <- IO {
                event.getPlayer.openInventory(inv)
            }.evalOn(EntityExecutionContext(event.getPlayer))
        yield ())
    private def itemUsedUnopened(event: PlayerInteractEvent): Unit =
        val stack = event.getItem
        val player = event.getPlayer

        if stack.getAmount == 1 then
            val _ = stack.setItemMeta(newItemMetaFor(stack))
        else
            stack.setAmount(stack.getAmount - 1)
            val newStack = stack.clone()
            newStack.setAmount(1)
            newStack.setItemMeta(newItemMetaFor(stack))
            player.getInventory.addItem(newStack).forEach { (_, stack) =>
                val _ =
                    player.getWorld.dropItemNaturally(player.getLocation, stack)
            }
