// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.UI

import BallCore.TextComponents
import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.`type`.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import com.github.stefvanschie.inventoryframework.pane.{
    Pane,
    OutlinePane as IFOutlinePane,
}
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextDecoration.State
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.inventory.{ItemFlag, ItemStack}
import org.bukkit.{Material, OfflinePlayer}

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

// trait AccumulatorFn:

object Accumulator:
    def run[T](inner: Accumulator[T, Unit] ?=> Unit): List[T] =
        run(inner, ())

    def run[T, E](inner: Accumulator[T, E] ?=> Unit, item: E): List[T] =
        given akku: Accumulator[T, E] = Accumulator(item)

        akku.items.toList

class Box[T](val initial: T):
    var it: T = initial

class Accumulator[T, E](val extra: E):
    var items: ArrayBuffer[T] = scala.collection.mutable.ArrayBuffer[T]()

    def ctx: E = extra

    def add(item: T): Unit =
        items.append(item)

type PaneAccumulator = Accumulator[Pane, Object => InventoryClickEvent => Unit]
type ItemAccumulator =
    Accumulator[GuiItem, Object => InventoryClickEvent => Unit]
type LoreAccumulator = Accumulator[Component, Box[Option[OfflinePlayer]]]

object ChatElements extends TextComponents

object Elements extends TextComponents:
    private def nil[T, E]: Accumulator[T, E] ?=> Unit = {}

    def Root(title: Component, rows: Int)(
        inner: PaneAccumulator ?=> Unit = nil
    )(using cb: Object => InventoryClickEvent => Unit): ChestGui =
        val chest = ChestGui(rows, ComponentHolder.of(title))
        Accumulator.run(inner, cb).foreach(x => chest.addPane(x))
        chest

    def OutlinePane(
        x: Int,
        y: Int,
        length: Int,
        height: Int,
        priority: Priority = Priority.NORMAL,
        repeat: Boolean = false,
    )(inner: ItemAccumulator ?=> Unit = nil)(using an: PaneAccumulator): Unit =
        val pane = IFOutlinePane(x, y, length, height, priority)
        pane.setRepeat(repeat)
        Accumulator.run(inner, an.extra).foreach(x => pane.addItem(x))
        an add pane

    def Item(item: ItemStack, displayName: Option[Component])(
        inner: LoreAccumulator ?=> Unit
    )(using an: ItemAccumulator): Unit =
        val is = item.clone()
        val im = is.getItemMeta

        displayName match
            case Some(x) =>
                im.displayName(x.style(x => {
                    x.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE)
                    ()
                }))
            case None =>
        val poki = Box[Option[OfflinePlayer]](None)
        im.lore(
            Accumulator
                .run(inner, poki)
                .map(line =>
                    line.style(x => {
                        x.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE)
                        ()
                    })
                )
                .asJava
        )

        poki.it match
            case Some(x) if im.isInstanceOf[SkullMeta] =>
                im.asInstanceOf[SkullMeta].setOwningPlayer(x)
            case _ =>

        is.setItemMeta(im)

        an add GuiItem(is, ev => ev.setCancelled(true))

    def Item(
        id: Material,
        amount: Int = 1,
        displayName: Option[Component] = None,
    )(inner: LoreAccumulator ?=> Unit = nil)(using an: ItemAccumulator): Unit =
        val is = ItemStack(id, amount)

        Item(is, displayName)(inner)

    def Button[Msg](
        id: Material,
        displayName: Component,
        onClick: Msg,
        amount: Int = 1,
        highlighted: Boolean = false,
    )(inner: LoreAccumulator ?=> Unit = nil)(using an: ItemAccumulator): Unit =
        val is = ItemStack(id, amount)
        val im = is.getItemMeta
        val baked = an.extra(onClick.asInstanceOf[Object])

        im.displayName(displayName.style(x => {
            x.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE)
            ()
        }))
        val poki = Box[Option[OfflinePlayer]](None)
        im.lore(
            Accumulator
                .run(inner, poki)
                .map(line =>
                    line.style(x => {
                        x.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE)
                        ()
                    })
                )
                .asJava
        )
        if highlighted then
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            val _ = im.addEnchant(Enchantment.DURABILITY, 1, true)

        poki.it match
            case Some(x) if im.isInstanceOf[SkullMeta] =>
                im.asInstanceOf[SkullMeta].setOwningPlayer(x)
            case _ =>

        is.setItemMeta(im)
        an add GuiItem(is, ev => baked(ev))

    def Lore(line: Component)(using an: LoreAccumulator): Unit =
        an add line

    def Skull(player: OfflinePlayer)(using an: LoreAccumulator): Unit =
        an.extra.it = Some(player)
