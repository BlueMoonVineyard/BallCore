// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Acclimation

import org.bukkit.entity.Player

object Extensions:
    extension (p: Player)(using accl: Storage)
        def temperatureAcclimation =
            accl.getTemperature(p.getUniqueId())
        def setTemperatureAcclimation(to: Double) =
            accl.setTemperature(p.getUniqueId(), to)

        def elevationAcclimation =
            accl.getElevation(p.getUniqueId())
        def setElevationAcclimation(to: Double) =
            accl.setElevation(p.getUniqueId(), to)
        def longitudeAcclimation =
            accl.getLongitude(p.getUniqueId())
        def setLongitudeAcclimation(to: Double) =
            accl.setLongitude(p.getUniqueId(), to)
        def latitudeAcclimation =
            accl.getLatitude(p.getUniqueId())
        def setLatitudeAcclimation(to: Double) =
            accl.setLatitude(p.getUniqueId(), to)
