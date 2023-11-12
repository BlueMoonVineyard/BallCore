// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import net.kyori.adventure.text.Component
import org.bukkit.event.block.{BlockBreakEvent, BlockPlaceEvent}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.{ItemStack, Recipe}
import org.bukkit.{Material, NamespacedKey}

object Listeners:
  trait BlockPlaced:
    def onBlockPlace(event: BlockPlaceEvent): Unit

  trait BlockRemoved:
    def onBlockRemoved(event: BlockBreakEvent): Unit

  trait BlockClicked:
    def onBlockClicked(event: PlayerInteractEvent): Unit

  trait BlockLeftClicked:
    def onBlockLeftClicked(event: PlayerInteractEvent): Unit

  trait ItemUsedOnBlock:
    def onItemUsedOnBlock(event: PlayerInteractEvent): Unit

  trait ItemUsed:
    def onItemUsed(event: PlayerInteractEvent): Unit

trait CustomItem:
  def group: ItemGroup

  def template: CustomItemStack

  def id = template.id

class PlainCustomItem(ig: ItemGroup, is: CustomItemStack) extends CustomItem:
  def group = ig

  def template = is

trait ItemRegistry:
  def register(item: CustomItem): Unit

  def lookup(from: NamespacedKey): Option[CustomItem]

  def lookup(from: ItemStack): Option[CustomItem]

  def addRecipe(it: Recipe): Unit

  def recipes(): List[NamespacedKey]

@SerialVersionUID(1000L)
enum CustomMaterial:
  case custom(named: String)
  case vanilla(named: Material)

  def template()(using r: ItemRegistry): Option[ItemStack] =
    this match
      case custom(named) =>
        r.lookup(NamespacedKey.fromString(named)).map(_.template)
      case vanilla(named) =>
        Some(ItemStack(named))

  def displayName()(using r: ItemRegistry): Component =
    this match
      case custom(named) =>
        r.lookup(NamespacedKey.fromString(named))
          .map(_.template.displayName())
          .getOrElse(Component.text(s"Invalid item ${named}"))
      case vanilla(named) =>
        ItemStack(named).displayName()

  def matches(other: ItemStack)(using r: ItemRegistry): Boolean =
    this match
      case custom(named) =>
        r.lookup(NamespacedKey.fromString(named))
          .map(_.template.isSimilar(other))
          .getOrElse(false)
      case vanilla(named) =>
        other.getType() == named
