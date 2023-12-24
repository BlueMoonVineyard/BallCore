package BallCore.Rails

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.vehicle.VehicleCreateEvent
import org.bukkit.entity.Minecart
import org.bukkit.plugin.Plugin
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.entity.Boat
import org.bukkit.Bukkit
import net.kyori.adventure.text.Component

class RailListener extends Listener:
    @EventHandler
    def onPlaceMinecart(event: VehicleCreateEvent): Unit =
        event.getVehicle() match
            case m: Minecart =>
                m.setMaxSpeed(4.5d)
            case _ => ()

    @EventHandler
    def onBoatMove(event: VehicleMoveEvent): Unit =
        event.getVehicle() match
            case b: Boat =>
                val vel = b.getVelocity()
                val velPlayer = b.getPassengers.getFirst().getVelocity()
                Bukkit.getServer().sendMessage(Component.text(s"vehicle moving ${vel} ${vel.length}"))
                Bukkit.getServer().sendMessage(Component.text(s"player moving ${velPlayer} ${velPlayer.length}"))
                if vel.length() >= 2.5d then
                    Bukkit.getServer().sendMessage(Component.text(s"nerfing boat from ${vel.length} to 2.5d"))
                    b.setVelocity(vel.normalize().multiply(2.5d))
            case _ => ()

object Rails:
    def register()(using p: Plugin): Unit =
        p.getServer().getPluginManager().registerEvents(RailListener(), p)
