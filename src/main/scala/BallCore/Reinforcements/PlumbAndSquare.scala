// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.ChatColor
import scala.util.chaining._
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.CustomItemStack
import BallCore.CustomItems.ItemGroup
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextDecoration.State
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.ClickType
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.CraftItemEvent
import BallCore.CustomItems.ItemRegistry
import org.bukkit.plugin.Plugin
import BallCore.UI.Prompts
import BallCore.Groups.GroupManager
import org.bukkit.event.player.PlayerInteractEntityEvent
import BallCore.Storage.SQLManager
import BallCore.Groups.nullUUID

object PlumbAndSquare:
    object CustomModelData:
        def from(kind: Option[ReinforcementTypes]): CustomModelData =
            kind match
                case None => Empty
                case Some(value) => from(value)
        def from(kind: ReinforcementTypes): CustomModelData =
            kind match
                case ReinforcementTypes.Stone => Stone
                case ReinforcementTypes.Deepslate => Deepslate
                case ReinforcementTypes.CopperLike => Copper
                case ReinforcementTypes.IronLike => Iron
    enum CustomModelData(val num: Int):
        case Empty extends CustomModelData(1)
        case Stone extends CustomModelData(2)
        case Deepslate extends CustomModelData(3)
        case Copper extends CustomModelData(4)
        case Iron extends CustomModelData(5)

    val group = ItemGroup(
        NamespacedKey("ballcore", "reinforcements"),
        ItemStack(Material.DIAMOND_PICKAXE),
    )
    val itemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "plumb_and_square"),
        Material.STICK,
        txt"Plumb-and-Square",
        defaultLore(),
    )
    itemStack.setItemMeta(
        itemStack
            .getItemMeta()
            .tap(_.setCustomModelData(CustomModelData.Empty.num))
    )
    val persistenceKeyCount =
        NamespacedKey("ballcore", "plumb_and_square_item_count")
    val persistenceKeyType =
        NamespacedKey("ballcore", "plumb_and_square_item_type")

    def defaultLore(): Component =
        txt"Can be crafted with reinforcement materials to protect mobs with"
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, State.FALSE)

    def itemCountLore(kind: ReinforcementTypes, count: Int): Component =
        val kindName = kind match
            case ReinforcementTypes.Stone =>
                txt"Stone"
                    .color(NamedTextColor.GRAY)
                    .decorate(TextDecoration.BOLD)
            case ReinforcementTypes.Deepslate =>
                txt"Deepslate"
                    .color(NamedTextColor.DARK_GRAY)
                    .decorate(TextDecoration.BOLD)
            case ReinforcementTypes.CopperLike =>
                txt"Red".color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
            case ReinforcementTypes.IronLike =>
                txt"White"
                    .color(NamedTextColor.WHITE)
                    .decorate(TextDecoration.BOLD)
        txt"Loaded with: ${kindName} Reinforcement × ${count}"
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, State.FALSE)

    val mainRecipe = NamespacedKey("ballcore", "plumb_and_square")
    val kinds = List(
        (NamespacedKey("ballcore", "plumb_and_square_stone"), Material.STONE),
        (NamespacedKey("ballcore", "plumb_and_square_deepslate"), Material.DEEPSLATE),
        (NamespacedKey("ballcore", "plumb_and_square_iron"), Material.IRON_INGOT),
        (NamespacedKey("ballcore", "plumb_and_square_copper"), Material.COPPER_INGOT),
    )

class PlumbAndSquare extends CustomItem:
    def group = PlumbAndSquare.group
    def template = PlumbAndSquare.itemStack

    def colourise(str: String): String =
        ChatColor.translateAlternateColorCodes('&', str)

    def getMaterials(item: ItemStack): Option[(ReinforcementTypes, Int)] =
        val keyCount = PlumbAndSquare.persistenceKeyCount
        val keyType = PlumbAndSquare.persistenceKeyType
        val meta = item.getItemMeta()
        val pdc = meta.getPersistentDataContainer()
        val kind = ReinforcementTypes.from(
            pdc.getOrDefault(keyType, PersistentDataType.STRING, "")
        )
        val loaded = pdc.getOrDefault(keyCount, PersistentDataType.INTEGER, 0)

        kind.map((_, loaded))

    def updateLore(item: ItemStack): Unit =
        val keyCount = PlumbAndSquare.persistenceKeyCount
        val keyType = PlumbAndSquare.persistenceKeyType
        val meta = item.getItemMeta()
        val pdc = meta.getPersistentDataContainer()
        val kind = ReinforcementTypes.from(
            pdc.getOrDefault(keyType, PersistentDataType.STRING, "")
        )
        val loaded = pdc.getOrDefault(keyCount, PersistentDataType.INTEGER, 0)
        meta.setCustomModelData(PlumbAndSquare.CustomModelData.from(kind).num)
        if kind.isDefined && loaded > 0 then
            val lore = List(
                PlumbAndSquare.defaultLore(),
                Component.empty(),
                PlumbAndSquare.itemCountLore(kind.get, loaded),
            ).asJava
            meta.lore(lore)
        else meta.lore(List(PlumbAndSquare.defaultLore()).asJava)

        val _ = item.setItemMeta(meta)

    def loadReinforcementMaterials(
        p: Player,
        item: ItemStack,
        count: Int,
        kind: ReinforcementTypes,
    ): Unit =
        if item.getAmount() > 1 then
            item.setAmount(item.getAmount() - 1)

            val separateItem = item.clone()
            separateItem.setAmount(1)
            loadReinforcementMaterials(p, separateItem, count, kind)

            if !p.getInventory().addItem(separateItem).isEmpty() then
                val _ = p
                    .getWorld()
                    .dropItemNaturally(p.getLocation(), separateItem)

            return

        val meta = item.getItemMeta()
        val keyCount = PlumbAndSquare.persistenceKeyCount
        val keyType = PlumbAndSquare.persistenceKeyType
        val pdc = meta.getPersistentDataContainer()
        val existingKind = ReinforcementTypes.from(
            pdc.getOrDefault(keyType, PersistentDataType.STRING, "")
        )

        if existingKind.isEmpty then
            pdc.set(keyType, PersistentDataType.STRING, kind.into())
        else if existingKind.get != kind then return

        val countLoaded =
            pdc.getOrDefault(keyCount, PersistentDataType.INTEGER, 0) + count
        if countLoaded == 0 then
            pdc.remove(keyType)
            pdc.remove(keyCount)
        else pdc.set(keyCount, PersistentDataType.INTEGER, countLoaded)
        item.setItemMeta(meta)
        updateLore(item)

class PlumbAndSquareListener()(using
    registry: ItemRegistry,
    p: Plugin,
    prompts: Prompts,
    gm: GroupManager,
    erm: EntityReinforcementManager,
    sql: SQLManager,
) extends org.bukkit.event.Listener:
    def reinforcementFromItem(is: ItemStack): Option[ReinforcementTypes] =
        if is == null then return None
        is.getType match
            case Material.STONE => Some(ReinforcementTypes.Stone)
            case Material.DEEPSLATE => Some(ReinforcementTypes.Deepslate)
            case Material.IRON_INGOT => Some(ReinforcementTypes.IronLike)
            case Material.COPPER_INGOT => Some(ReinforcementTypes.CopperLike)
            case _ => None

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onInteractEntity(event: PlayerInteractEntityEvent): Unit =
        val p = event.getPlayer()
        val i = p.getInventory()
        val istack = i.getItemInMainHand()
        val item = registry.lookup(istack)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then return
        if !RuntimeStateManager.states.contains(p.getUniqueId()) then
            p.sendServerMessage(
                txt"Shift left-click the plumb-and-square in your inventory to set a group to reinforce on before reinforcing"
            )
            event.setCancelled(true)
            return

        val pas = item.get.asInstanceOf[PlumbAndSquare]
        val mats = pas.getMaterials(istack)
        if mats.isEmpty then return
        val (kind, amount) = mats.get
        if amount < 1 then return

        val gid = RuntimeStateManager.states(p.getUniqueId())
        val eid = event.getRightClicked().getUniqueId()
        erm.reinforce(p.getUniqueId(), gid, nullUUID, eid, kind) match
            case Left(err) =>
                event.getPlayer().sendMessage(explain(err))
            case Right(value) =>
                Listener.playCreationEffect(
                    event.getRightClicked().getLocation(),
                    kind,
                )
                pas.loadReinforcementMaterials(p, istack, -1, kind)
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onShiftLeftClick(event: InventoryClickEvent): Unit =
        if event.getClick() != ClickType.SHIFT_LEFT then return
        val h = event.getWhoClicked()
        if !h.isInstanceOf[Player] then return
        val p = h.asInstanceOf[Player]
        val istack = event.getCurrentItem()
        val item = registry.lookup(istack)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then return

        event.setCancelled(true)
        p.closeInventory()
        given ctx: ExecutionContext = EntityExecutionContext(p)
        prompts.prompt(p, "What group do you want to reinforce on?").foreach {
            group =>
                sql.useBlocking(
                    sql.withS(sql.withTX(gm.userGroups(p.getUniqueId()).value))
                ).map(
                    _.find(
                        _.name.toLowerCase().contains(group.toLowerCase())
                    )
                ) match
                    case Left(err) =>
                        p.sendMessage(err.explain())
                    case Right(Some(group)) =>
                        RuntimeStateManager.states(p.getUniqueId()) = group.id
                    case Right(None) =>
                        p.sendServerMessage(
                            txt"I couldn't find a group matching '${group}'"
                        )
        }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onPrepareCraft(event: PrepareItemCraftEvent): Unit =
        val inv = event.getInventory()
        val recp = inv.getRecipe()
        if recp == null then return

        val res = recp.getResult().clone()
        val item = registry.lookup(res)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then return

        val h = event.getView().getPlayer()
        if !h.isInstanceOf[Player] then return

        val p = h.asInstanceOf[Player]
        val pas = item.get.asInstanceOf[PlumbAndSquare]
        val pasStack =
            inv.getItem(inv.first(PlumbAndSquare.itemStack.getType()))
        val existingMats = pas.getMaterials(pasStack)
        val craftingWith = inv
            .getMatrix()
            .filterNot(_ == null)
            .filterNot(_.getType() == PlumbAndSquare.itemStack.getType())(0)

        if !existingMats.isEmpty then
            val (kind, count) = existingMats.get
            if Some(kind) != reinforcementFromItem(craftingWith) then
                inv.setResult(ItemStack(Material.AIR))
                return

        val kind = reinforcementFromItem(craftingWith).get
        val newStack = pasStack.clone()
        newStack.setAmount(1)
        pas.loadReinforcementMaterials(
            p,
            newStack,
            craftingWith.getAmount(),
            kind,
        )

        inv.setResult(newStack)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onDoCraft(event: CraftItemEvent): Unit =
        val inv = event.getInventory()
        val res = inv.getResult()

        if res == null then return

        val item = registry.lookup(res)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then return

        val craftingWith = inv
            .getMatrix()
            .filterNot(_ == null)
            .filterNot(_.getType() == PlumbAndSquare.itemStack.getType())(0)
        craftingWith.setAmount(0)
