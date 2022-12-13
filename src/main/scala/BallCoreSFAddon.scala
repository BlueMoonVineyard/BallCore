package BallCore

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import org.bukkit.plugin.java.JavaPlugin

final class BallCoreSFAddon()(using ballcore: Main) extends SlimefunAddon:
    override def getJavaPlugin(): JavaPlugin =
        ballcore
    override def getBugTrackerURL(): String =
        "https://github.com/BlueMoonVineyard/BallCore"