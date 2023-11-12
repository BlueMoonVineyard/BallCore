// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.Material
import org.bukkit.block.data.`type`.*
import org.bukkit.block.data.`type`.Chest.Type
import org.bukkit.block.{Block, BlockFace}

object BlockAdjustment:

  import org.bukkit.Material.*

  private def counterclockwise(face: BlockFace): BlockFace =
    face match
      case BlockFace.NORTH => BlockFace.WEST
      case BlockFace.EAST  => BlockFace.NORTH
      case BlockFace.SOUTH => BlockFace.EAST
      case BlockFace.WEST  => BlockFace.SOUTH
      case _               => face

  // there's a lot of multiblock things in minecraft, so this is responsible for choosing which block is the
  // "responsible" block
  def adjustBlock(block: Block): Block =
    block.getType match
      case CHEST | TRAPPED_CHEST =>
        val chest = block.getBlockData.asInstanceOf[Chest]
        val facing = chest.getFacing
        chest.getType match
          // single chests go to themselves
          case Type.SINGLE =>
            block
          // for double chests, we prefer the left one to have the reinforement
          case Type.LEFT =>
            block
          case Type.RIGHT =>
            val newFace = counterclockwise(facing)
            val otherBlock =
              block.getLocation().add(newFace.getDirection).getBlock
            otherBlock
      // plants defer to the block below them
      case DANDELION | POPPY | BLUE_ORCHID | ALLIUM | AZURE_BLUET |
          ORANGE_TULIP | RED_TULIP | PINK_TULIP | WHITE_TULIP | OXEYE_DAISY |
          ACACIA_SAPLING | BIRCH_SAPLING | DARK_OAK_SAPLING | JUNGLE_SAPLING |
          OAK_SAPLING | SPRUCE_SAPLING | WARPED_FUNGUS | CRIMSON_FUNGUS |
          BAMBOO_SAPLING | FLOWERING_AZALEA | WHEAT | CARROTS | POTATOES |
          BEETROOTS | SWEET_BERRY_BUSH | MELON_STEM | PUMPKIN_STEM |
          ATTACHED_MELON_STEM | ATTACHED_PUMPKIN_STEM | WARPED_ROOTS |
          CRIMSON_ROOTS | NETHER_SPROUTS | WITHER_ROSE | LILY_OF_THE_VALLEY |
          CORNFLOWER | SEA_PICKLE | FERN | KELP | GRASS | SEAGRASS |
          TUBE_CORAL | TUBE_CORAL_FAN | BRAIN_CORAL | BRAIN_CORAL_FAN |
          BUBBLE_CORAL | BUBBLE_CORAL_FAN | FIRE_CORAL | FIRE_CORAL_FAN |
          HORN_CORAL | HORN_CORAL_FAN | DEAD_TUBE_CORAL | DEAD_TUBE_CORAL_FAN |
          DEAD_BRAIN_CORAL | DEAD_BRAIN_CORAL_FAN | DEAD_BUBBLE_CORAL |
          DEAD_BUBBLE_CORAL_FAN | DEAD_FIRE_CORAL | DEAD_FIRE_CORAL_FAN |
          DEAD_HORN_CORAL | DEAD_HORN_CORAL_FAN | SMALL_DRIPLEAF |
          NETHER_WART =>
        block.getRelative(BlockFace.DOWN)
      // tall plants need to scan downwards to find the block they want to defer to
      case SUGAR_CANE | BAMBOO | ROSE_BUSH | TWISTING_VINES_PLANT |
          BIG_DRIPLEAF_STEM | CACTUS | SUNFLOWER | LILAC | TALL_GRASS |
          LARGE_FERN | TALL_SEAGRASS | KELP_PLANT | PEONY =>
        var below = block.getRelative(BlockFace.DOWN)
        while below.getType == block.getType do
          below = below.getRelative(BlockFace.DOWN)
        below
      // these funny plants are anchored to the ceiling
      case SPORE_BLOSSOM | HANGING_ROOTS =>
        block.getRelative(BlockFace.UP)

      // doors defer to the bottom block
      case ACACIA_DOOR | BIRCH_DOOR | DARK_OAK_DOOR | IRON_DOOR | SPRUCE_DOOR |
          JUNGLE_DOOR | WARPED_DOOR | CRIMSON_DOOR | OAK_DOOR =>
        if block.getRelative(BlockFace.UP).getType != block.getType then
          block.getRelative(BlockFace.DOWN)
        else block

      // beds defer to the bed base
      case BLACK_BED | BLUE_BED | BROWN_BED | CYAN_BED | GRAY_BED | GREEN_BED |
          MAGENTA_BED | LIME_BED | ORANGE_BED | PURPLE_BED | PINK_BED |
          WHITE_BED | LIGHT_GRAY_BED | LIGHT_BLUE_BED | RED_BED | YELLOW_BED =>
        val bed = block.getBlockData.asInstanceOf[Bed]
        if bed.getPart == Bed.Part.HEAD then
          block.getRelative(bed.getFacing.getOppositeFace)
        else block

      // these blocks defer to the block they're anchored on
      case TUBE_CORAL_WALL_FAN | BRAIN_CORAL_WALL_FAN | BUBBLE_CORAL_WALL_FAN |
          FIRE_CORAL_WALL_FAN | HORN_CORAL_WALL_FAN | DEAD_TUBE_CORAL_WALL_FAN |
          DEAD_BRAIN_CORAL_WALL_FAN | DEAD_BUBBLE_CORAL_WALL_FAN |
          DEAD_FIRE_CORAL_WALL_FAN | DEAD_HORN_CORAL_WALL_FAN =>
        val cwf = block.getBlockData.asInstanceOf[CoralWallFan]
        block.getRelative(cwf.getFacing.getOppositeFace)
      // so do these
      case SMALL_AMETHYST_BUD | MEDIUM_AMETHYST_BUD | LARGE_AMETHYST_BUD =>
        val amethyst = block.getBlockData.asInstanceOf[AmethystCluster]
        block.getRelative(amethyst.getFacing.getOppositeFace)
      // weeping vines come from the ceiling
      case WEEPING_VINES =>
        // scan upwards
        var above = block.getRelative(BlockFace.UP)
        while above.getType == block.getType || above.getType == Material.WEEPING_VINES_PLANT
        do above = above.getRelative(BlockFace.UP)
        above
      // cave vines also come from the ceiling
      case CAVE_VINES =>
        var above = block.getRelative(BlockFace.UP)
        while above.getType == block.getType || above.getType == Material.CAVE_VINES_PLANT
        do above = above.getRelative(BlockFace.UP)
        above
      // these also come from the ceiling
      case CAVE_VINES_PLANT | WEEPING_VINES_PLANT =>
        var above = block.getRelative(BlockFace.UP)
        while above.getType == block.getType do
          above = above.getRelative(BlockFace.UP)
        above
      // twisting vines come from the floor or a twisting vine plant
      case TWISTING_VINES =>
        var below = block.getRelative(BlockFace.DOWN)
        while below.getType == block.getType || below.getType == Material.TWISTING_VINES_PLANT
        do below = below.getRelative(BlockFace.DOWN)
        below
      // dripleafs come from the floor or a dripleaf stem
      case BIG_DRIPLEAF =>
        var below = block.getRelative(BlockFace.DOWN)
        while below.getType == block.getType || below.getType == Material.BIG_DRIPLEAF_STEM
        do below = below.getRelative(BlockFace.DOWN)
        below
      // dripstones come from either the floor or the ceiling (funky!)
      case POINTED_DRIPSTONE =>
        val dripstone = block.getBlockData.asInstanceOf[PointedDripstone]
        var direction =
          block.getRelative(dripstone.getVerticalDirection.getOppositeFace);
        while direction.getType == block.getType do
          direction = direction.getRelative(
            dripstone.getVerticalDirection.getOppositeFace
          )
        direction
      // everything else is fine
      case _ =>
        block
