// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Plants

import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.{Block, BlockFace, BlockState}
import org.bukkit.util.Consumer
import scala.util.chaining.*
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import io.sentry.Sentry

object PlantGrower:
    // return value is whether or not the plant is "done"
    def grow(block: Block, what: PlantType): Try[Boolean] =
        Try(
            what match
                case PlantType.ageable(mat, _) =>
                    block.setBlockData(
                        block.getBlockData
                            .asInstanceOf[Ageable]
                            .tap(x =>
                                x.setAge((x.getAge + 1).min(x.getMaximumAge))
                            ),
                        true,
                    )
                    block.getBlockData
                        .asInstanceOf[Ageable]
                        .pipe(x => x.getMaximumAge == x.getAge)
                case PlantType.generateTree(mat, kind, _) =>
                    val consumer: Consumer[BlockState] = state => {}
                    block.setType(Material.AIR, true)
                    Try(
                        block.getWorld
                            .generateTree(
                                block.getLocation(),
                                java.util.Random(),
                                kind,
                                consumer,
                            )
                    ) match
                        case Failure(exception) =>
                            Sentry.captureException(exception)
                            exception.printStackTrace()
                            block.setType(mat, false)
                            false
                        case Success(true) =>
                            true
                        case Success(false) =>
                            block.setType(Material.DEAD_BUSH, false)
                            false
                case PlantType.stemmedAgeable(stem, fruit, _) =>
                    val ageable = block.getBlockData.asInstanceOf[Ageable]
                    ageable.setAge(
                        (ageable.getAge + 1).min(ageable.getMaximumAge)
                    )
                    block.setBlockData(ageable)
                    if ageable.getAge == ageable.getMaximumAge then
                        block.setType(fruit, true)
                        true
                    else false
                case PlantType.verticalPlant(mat, _) =>
                    val upOne = block.getRelative(BlockFace.UP)
                    val upTwo = upOne.getRelative(BlockFace.UP)
                    if upOne.getType == Material.AIR then
                        upOne.setType(mat, true)
                        false
                    else if upOne.getType == mat && upTwo.getType == Material.AIR
                    then
                        upTwo.setType(mat, true)
                        true
                    else false
                case PlantType.bamboo(_) =>
                    ???
                case PlantType.fruitTree(looksLike, fruit, _) =>
                    ???
        )
