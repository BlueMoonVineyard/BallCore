package BallCore.NoodleEditor

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.Color
import org.bukkit.Particle
import java.util.Arrays
import org.bukkit.Particle.DustOptions
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.block.Block
import org.bukkit.plugin.Plugin

def drawLine(
    fromBlock: Location,
    toBlock: Location,
    showingTo: Player,
    color: Color,
    detail: Double,
): Unit =
    val start = fromBlock.clone()
    val finish = toBlock.clone()

    val dir = finish.toVector.subtract(start.toVector).normalize()
    val len = start.distance(finish)

    for i <- Iterator.iterate(0.0)(_ + detail).takeWhile(_ <= len) do
        val offset = dir.clone().multiply(i)
        val pos = start.clone().add(offset)

        pos.getWorld
            .spawnParticle(
                Particle.REDSTONE,
                Arrays.asList(showingTo),
                showingTo,
                pos.getX,
                pos.getY,
                pos.getZ,
                4,
                0.0,
                0.0,
                0.0,
                0.0,
                DustOptions(color, 1.0),
            )

class EditorListener()(using e: NoodleEditor) extends Listener:
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def interact(event: PlayerInteractEvent): Unit =
        if event.getHand != EquipmentSlot.HAND then return

        event.getAction match
            case Action.RIGHT_CLICK_BLOCK =>
                if e.clicked(event.getPlayer, event.getClickedBlock) then
                    event.setCancelled(true)
            case Action.LEFT_CLICK_BLOCK =>
                if e.leftClicked(event.getPlayer, event.getClickedBlock)
                then event.setCancelled(true)
            case _ =>

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def look(event: PlayerMoveEvent): Unit =
        val result = event.getPlayer.rayTraceBlocks(30.0)
        if result == null then return
        val location =
            result.getHitPosition.toLocation(event.getPlayer.getWorld)
        val block = result.getHitBlock

        e.look(event.getPlayer, location, block)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def quit(event: PlayerQuitEvent): Unit =
        e.leave(event.getPlayer)

object NoodleEditor:
    def register()(using p: Plugin): NoodleEditor =
        given NoodleEditor = NoodleEditor()
        p.getServer().getPluginManager().registerEvents(EditorListener(), p)
        summon[NoodleEditor]

class NoodleEditor:
    def leave(p: Player): Unit =
        ()

    def look(p: Player, l: Location, b: Block): Unit =
        ()

    def clicked(p: Player, b: Block): Boolean =
        false

    def leftClicked(p: Player, b: Block): Boolean =
        false
