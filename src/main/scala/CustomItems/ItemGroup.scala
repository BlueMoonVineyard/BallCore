// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import scala.util.chaining._
import scala.jdk.CollectionConverters._
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextDecoration.State
import net.kyori.adventure.text.format.NamedTextColor

class CustomItemStack(
    val itemID: NamespacedKey,
    val stack: ItemStack
) extends ItemStack(stack):
    val id = itemID
    setItemMeta(
        getItemMeta()
            .tap(_.getPersistentDataContainer().set(BasicItemRegistry.persistenceKeyID, PersistentDataType.STRING, id.toString()))
    )

object CustomItemStack:
    def loreify(a: Component): Component =
        a.style(x => { x.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE).colorIfAbsent(NamedTextColor.GRAY); () })
    def make(itemID: NamespacedKey, stack: Material, name: Component, lore: Component*): CustomItemStack =
        val is = ItemStack(stack)
        is.setItemMeta(
            is.getItemMeta()
                .tap(_.displayName(name.style(x => { x.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE).colorIfAbsent(NamedTextColor.WHITE); () })))
                .tap(_.lore(lore.map(loreify).asJava))
        )
        CustomItemStack(itemID, is)

case class ItemGroup(
    key: NamespacedKey,
    gui: ItemStack,
)
