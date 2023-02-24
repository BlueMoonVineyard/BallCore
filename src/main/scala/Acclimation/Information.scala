// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Acclimation

import org.bukkit.World
import org.bukkit.Bukkit
import java.util.UUID
import org.bukkit.Location
import org.bukkit.block.Biome

object Information:
    val WorldRadiusBlocks = 4000*4
    val SeaLevel = 67

    def latLong(x: Float, z: Float): (Float, Float) =
        (z / WorldRadiusBlocks, x / WorldRadiusBlocks)

    def temperature(x: Int, y: Int, z: Int): Float =
        val world = Bukkit.getWorld("world")
        val provider = world.getBiomeProvider()
        val temp = world.getBlockAt(x, y, z).getTemperature()
        // coldest is -0.7, hottest is 2.0
        ((temp + 0.7) / 2.7).toFloat

    private def normalize(v: Float, min: Float, max: Float): Float =
        (v - min) / (max - min)

    def elevation(y: Int): Float =
        val world = Bukkit.getWorld("world")
        if y < SeaLevel then
            -normalize(y, world.getMinHeight, SeaLevel)
        else
            normalize(y, SeaLevel, world.getMaxHeight)
