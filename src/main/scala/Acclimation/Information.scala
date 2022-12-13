package BallCore.Acclimation

import org.bukkit.World
import org.bukkit.Bukkit
import java.util.UUID
import org.bukkit.Location

object Information:
    val WorldRadiusBlocks = 4000*4
    val SeaLevel = 67

    def latLong(x: Float, z: Float): (Float, Float) =
        (z / WorldRadiusBlocks, x / WorldRadiusBlocks)

    def temperature(x: Int, z: Int): Float =
        val biome = Bukkit.getWorld("world").getBiome(x, z)
        ???

    private def normalize(v: Float, min: Float, max: Float): Float =
        (v - min) / (max - min)

    def elevation(y: Int): Float =
        val world = Bukkit.getWorld("world")
        if y < SeaLevel then
            -normalize(y, world.getMinHeight, SeaLevel)
        else
            normalize(y, SeaLevel, world.getMaxHeight)
