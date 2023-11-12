// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Acclimation

import org.bukkit.Bukkit

object Information:
    private val WorldRadiusBlocks = 2000.0
    private val ExclusionZoneSize = 200.0
    private val SeaLevel = 67

    // -1.0 to 1.0, returns 0.0 to 1.0, with 1.0 being the same point and
    // 0.0 being opposite extrema of the spectrum
    def similarityNeg(a: Double, b: Double): Double =
        val adjustedA = (a + 1.0) / 2.0
        val adjustedB = (b + 1.0) / 2.0

        1.0 - (adjustedA - adjustedB).abs

    // 0.0 to 1.0, being 0.0 the closer you are to 0 on any one of the xz axes
    // and being 1.0 the further away you are from 0 on any one of the xz axes
    // its like a sort of trench thing
    def exclusionZoneFactor(x: Double, z: Double): Double =
        ((x * x) / (ExclusionZoneSize * ExclusionZoneSize)).min(
            1
        ) * ((z * z) / (ExclusionZoneSize * ExclusionZoneSize)).min(1)

    def latLong(x: Double, z: Double): (Double, Double) =
        (z / WorldRadiusBlocks, x / WorldRadiusBlocks)

    def temperature(x: Int, y: Int, z: Int): Double =
        val world = Bukkit.getWorld("world")
        val temp = world.getTemperature(x, y, z)
        // coldest is -0.7, hottest is 2.0
        (temp + 0.7) / 2.7

    def humidity(x: Int, y: Int, z: Int): Double =
        val world = Bukkit.getWorld("world")
        val humidity = world.getHumidity(x, y, z)
        humidity

    private def normalize(v: Double, min: Double, max: Double): Double =
        (v - min) / (max - min)

    def elevation(y: Int): Double =
        val world = Bukkit.getWorld("world")
        if y < SeaLevel then -normalize(y, world.getMinHeight, SeaLevel)
        else normalize(y, SeaLevel, world.getMaxHeight)
