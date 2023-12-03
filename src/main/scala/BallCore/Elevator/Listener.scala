package BallCore.Elevator

import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.Location
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import BallCore.TextComponents._
import org.bukkit.event.EventHandler
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.key.Key

class Listener extends org.bukkit.event.Listener:
    val elevatorMaterial = Material.LODESTONE
    val sound = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.PLAYER, 1f, 10f)

    private def attemptTeleport(potentialElevator: Block, player: Player): Boolean =
        if potentialElevator.getType() != elevatorMaterial then
            return false

        val up1 = potentialElevator.getRelative(BlockFace.UP)
        val up2 = up1.getRelative(BlockFace.UP)

        if !up1.isPassable() || !up2.isPassable() then
            return false

        val playerLoc = player.getLocation()

        val loc = potentialElevator.getLocation()
        loc.add(0.5, 1.1, 0.5)
        loc.setYaw(playerLoc.getYaw())
        loc.setPitch(playerLoc.getPitch())

        val _ = player.teleportAsync(loc)

        potentialElevator.getWorld().playSound(sound, loc.getX(), loc.getY(), loc.getZ())

        true

    @EventHandler
    def onSneak(event: PlayerToggleSneakEvent): Unit =
        if !event.isSneaking() then
            return ()

        val player = event.getPlayer()
        val block = player.getLocation().getBlock().getRelative(BlockFace.DOWN)
        val world = block.getWorld()
        val fromY = (y: Int) => Location(world, block.getLocation().getX(), y, block.getLocation().getZ()).getBlock()

        if block.getType() != elevatorMaterial then
            return ()

        if !(block.getY()-1 to block.getWorld().getMinHeight() by -1).map(fromY).exists { block =>
            attemptTeleport(block, player)
        } then
            player.sendServerMessage(txt"There aren't any lodestones below you.")

    @EventHandler
    def onJump(event: PlayerJumpEvent): Unit =
        val player = event.getPlayer()
        val block = player.getLocation().getBlock().getRelative(BlockFace.DOWN)
        val world = block.getWorld()
        val fromY = (y: Int) => Location(world, block.getLocation().getX(), y, block.getLocation().getZ()).getBlock()

        if block.getType() != elevatorMaterial then
            return ()

        if !(block.getY()+1 to block.getWorld().getMaxHeight()).map(fromY).exists { block =>
            attemptTeleport(block, player)
        } then
            player.sendServerMessage(txt"There aren't any lodestones above you.")