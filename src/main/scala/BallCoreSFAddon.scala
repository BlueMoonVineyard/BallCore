// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import org.bukkit.plugin.java.JavaPlugin

final class BallCoreSFAddon()(using ballcore: Main) extends SlimefunAddon:
    override def getJavaPlugin(): JavaPlugin =
        ballcore
    override def getBugTrackerURL(): String =
        "https://github.com/BlueMoonVineyard/BallCore"