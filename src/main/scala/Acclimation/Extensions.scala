package BallCore.Acclimation

import org.bukkit.entity.Player

object Extensions:
    extension (p: Player)(using accl: Storage)
        def temperatureAcclimation =
            accl.getTemperature(p.getUniqueId())
        def setTemperatureAcclimation(to: Float) =
            accl.setTemperature(p.getUniqueId(), to)

        def elevationAcclimation =
            accl.getElevation(p.getUniqueId())
        def setElevationAcclimation(to: Float) =
            accl.setElevation(p.getUniqueId(), to)
        def longitudeAcclimation =
            accl.getLongitude(p.getUniqueId())
        def setLongitudeAcclimation(to: Float) =
            accl.setLongitude(p.getUniqueId(), to)
        def latitudeAcclimation =
            accl.getLatitude(p.getUniqueId())
        def setLatitudeAcclimation(to: Float) =
            accl.setLatitude(p.getUniqueId(), to)
