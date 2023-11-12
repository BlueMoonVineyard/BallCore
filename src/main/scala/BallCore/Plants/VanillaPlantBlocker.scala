// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Plants

import org.bukkit.Material
import org.bukkit.block.data.`type`.Dispenser
import org.bukkit.event.block.{Action, BlockBreakEvent, BlockDispenseEvent, BlockGrowEvent}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.StructureGrowEvent
import org.bukkit.event.{EventHandler, EventPriority, Listener}

import scala.util.Random
import scala.util.chaining.*

object VanillaPlantBlocker:
  private val bonemealables: Set[Material] = Set(
    Material.ACACIA_SAPLING,
    Material.BAMBOO,
    Material.BAMBOO_SAPLING,
    Material.BEETROOTS,
    Material.BIG_DRIPLEAF,
    Material.BIG_DRIPLEAF_STEM,
    Material.BIRCH_SAPLING,
    Material.BROWN_MUSHROOM,
    Material.CACTUS,
    Material.CARROTS,
    Material.CAVE_VINES,
    Material.CAVE_VINES_PLANT,
    Material.COCOA,
    Material.DARK_OAK_SAPLING,
    Material.FLOWERING_AZALEA,
    Material.JUNGLE_SAPLING,
    Material.KELP,
    Material.KELP_PLANT,
    Material.MELON_STEM,
    Material.NETHER_WART,
    Material.OAK_SAPLING,
    Material.POTATOES,
    Material.PUMPKIN_STEM,
    Material.RED_MUSHROOM,
    Material.SEA_PICKLE,
    Material.SPRUCE_SAPLING,
    Material.SUGAR_CANE,
    Material.SWEET_BERRY_BUSH,
    Material.TWISTING_VINES,
    Material.TWISTING_VINES_PLANT,
    Material.WEEPING_VINES,
    Material.WEEPING_VINES_PLANT,
    Material.WHEAT
  )

class VanillaPlantBlocker() extends Listener:
  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  def onBigPlantGrow(event: StructureGrowEvent): Unit =
    event.setCancelled(true)

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  def onPlantGrow(event: BlockGrowEvent): Unit =
    event.setCancelled(true)

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  def onPlayerBonemeal(event: PlayerInteractEvent): Unit =
    if event.getAction != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock == null
    then
      return if event.getItem == null || event.getItem.getType != Material.BONE_MEAL
      then
        return if VanillaPlantBlocker.bonemealables.contains(
            event.getClickedBlock.getType
          )
        then event.setCancelled(true)

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  def onDispenserBonemeal(event: BlockDispenseEvent): Unit =
    if event.getItem == null || event.getItem.getType != Material.BONE_MEAL
    then return
    val block = event.getBlock
    val target = block.getBlockData
      .asInstanceOf[Dispenser]
      .pipe(disp => block.getRelative(disp.getFacing))

    if VanillaPlantBlocker.bonemealables.contains(target.getType) then
      event.setCancelled(true)

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  def onHarvest(event: BlockBreakEvent): Unit =

    if !(event.getBlock.getType == Material.GRASS || event.getBlock.getType == Material.TALL_GRASS)
    then
      return if event.getPlayer.getInventory.getItemInMainHand == Material.SHEARS
      then return if !(Random.nextDouble() < 0.125) then return

    val loc = event.getBlock
    val climate = Climate.climateAt(loc.getX, loc.getY, loc.getZ)
    val possiblePlants = Plant.values.filter(_.growingClimate == climate)
    val chosenPlant = possiblePlants(Random.nextInt(possiblePlants.length))

    loc.getWorld
      .dropItemNaturally(
        loc.getLocation(),
        chosenPlant.plant.representativeItem()
      )
    ()
