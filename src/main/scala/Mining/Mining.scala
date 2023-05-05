package BallCore.Mining

import org.bukkit.Material
import BallCore.Acclimation
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import BallCore.Acclimation.Information

object Mining:
    val stoneBlocks = Set(Material.STONE, Material.DEEPSLATE, Material.TUFF)

class MiningListener()(using ac: AntiCheeser, as: Acclimation.Storage) extends Listener:
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onBreakBlock(event: BlockBreakEvent): Unit =
        val plr = event.getPlayer().getUniqueId()
        val (lat, long) = Information.latLong(event.getBlock().getX(), event.getBlock().getZ())
        val (plat, plong) = (as.getLatitude(plr), as.getLongitude(plr))

        val dlat = Information.similarityNeg(lat, plat)
        val dlong = Information.similarityNeg(long, plong)

        // multiplier of the bonus on top of baseline rate
        val rateMultiplier = math.sqrt((dlat*dlat) + (dlong*dlong))
