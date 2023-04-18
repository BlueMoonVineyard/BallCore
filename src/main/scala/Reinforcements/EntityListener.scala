package BallCore.Reinforcements

import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.EventPriority
import BallCore.CustomItems.ItemRegistry
import BallCore.Groups.GroupManager
import BallCore.Groups.Permissions
import org.bukkit.event.Listener

class EntityListener()(using erm: EntityReinforcementManager, registry: ItemRegistry, gm: GroupManager) extends Listener:
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
    def onInteractEntity(event: PlayerInteractEntityEvent): Unit =
        val p = event.getPlayer()
        val i = p.getInventory()
        val istack = i.getItemInMainHand()
        val item = registry.lookup(istack)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then
            return
        if !RuntimeStateManager.states.contains(p.getUniqueId()) then
            p.sendMessage("Shift left-click the plumb-and-square in your inventory to set a group to reinforce on before reinforcing")
            event.setCancelled(true)
            return

        val pas = item.get.asInstanceOf[PlumbAndSquare]
        val mats = pas.getMaterials(istack)
        if mats.isEmpty then
            return
        val (kind, amount) = mats.get
        if amount < 1 then
            return

        val gid = RuntimeStateManager.states(p.getUniqueId())
        val eid = event.getRightClicked().getUniqueId()
        erm.reinforce(p.getUniqueId(), gid, eid, kind) match
            case Left(err) =>
                event.getPlayer().sendMessage(explain(err))
            case Right(value) =>
                playCreationEffect(event.getRightClicked().getLocation(), kind)
                pas.loadReinforcementMaterials(p, istack, -1, kind)

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
