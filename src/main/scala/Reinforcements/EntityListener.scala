package BallCore.Reinforcements

import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.EventPriority
import BallCore.Groups.GroupManager
import BallCore.Groups.Permissions
import org.bukkit.event.Listener
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.entity.Player

class EntityListener()(using erm: EntityReinforcementManager, gm: GroupManager) extends Listener:
    import BallCore.Reinforcements.Listener._

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onDamageEntity(event: EntityDamageEvent): Unit =
        val ent = event.getEntity()
        erm.damage(ent.getUniqueId()) match
            case Left(err) =>
                err match
                    case JustBroken(bs) =>
                        playBreakEffect(ent.getLocation(), bs.kind)
                    case _ =>
                        ()
            case Right(value) =>
                playDamageEffect(ent.getLocation(), value.kind)
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onDamageVehicle(event: VehicleDamageEvent): Unit =
        val ent = event.getVehicle()
        erm.damage(ent.getUniqueId()) match
            case Left(err) =>
                err match
                    case JustBroken(bs) =>
                        playBreakEffect(ent.getLocation(), bs.kind)
                    case _ =>
                        ()
            case Right(value) =>
                playDamageEffect(ent.getLocation(), value.kind)
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onEnterVehicle(event: VehicleEnterEvent): Unit =
        val rein = erm.getReinforcement(event.getVehicle().getUniqueId())
        if rein.isEmpty then
            return
        val reinf = rein.get
        val ent = event.getEntered()
        if !ent.isInstanceOf[Player] then
            event.setCancelled(true)
            return
        gm.check(ent.asInstanceOf[Player].getUniqueId(), reinf.group, Permissions.Entities) match
            case Right(ok) if ok =>
                ()
            case _ =>
                // TODO: notify of permission denied
                event.setCancelled(true)

    // prevent interacting with reinforced entities
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventEntityInteractions(event: PlayerInteractEntityEvent): Unit =
        val rein = erm.getReinforcement(event.getRightClicked().getUniqueId())
        if rein.isEmpty then
            return
        val reinf = rein.get
        gm.check(event.getPlayer().getUniqueId(), reinf.group, Permissions.Entities) match
            case Right(ok) if ok =>
                ()
            case _ =>
                // TODO: notify of permission denied
                event.setCancelled(true)
