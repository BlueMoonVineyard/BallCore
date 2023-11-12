// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Plants

import org.bukkit.event.block.{Action, BlockPlaceEvent}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.{Material, Tag}

import scala.util.chaining.*

class CustomPlantListener()(using pbm: PlantBatchManager) extends Listener:
  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  def onPlantCrop(event: BlockPlaceEvent): Unit =
    val plant = Plant.values.find { p =>
      p.plant match
        case PlantType.ageable(mat, _) =>
          mat == event.getBlockPlaced.getType
        case PlantType.generateTree(mat, kind, _) =>
          mat == event.getBlockPlaced.getType
        case PlantType.stemmedAgeable(stem, fruit, _) =>
          stem == event.getBlockPlaced.getType
        case PlantType.verticalPlant(mat, _) =>
          mat == event.getBlockPlaced.getType
        case PlantType.bamboo(_) =>
          Material.BAMBOO == event.getBlockPlaced.getType
        case PlantType.fruitTree(looksLike, fruit, _) =>
          false
    }
    plant match
      case None =>
      case Some(what) =>
        pbm.send(PlantMsg.startGrowing(what, event.getBlock))

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  def inspectPlant(event: PlayerInteractEvent): Unit =
    if event.getHand != EquipmentSlot.HAND || event.getAction != Action.RIGHT_CLICK_BLOCK
    then
      return if event.getItem == null || !Tag.ITEMS_HOES.isTagged(
          event.getItem.getType
        )
      then
        return pbm.send(
          PlantMsg.inspect(event.getClickedBlock, event.getPlayer)
        )
