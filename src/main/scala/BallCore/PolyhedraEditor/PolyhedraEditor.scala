package BallCore.PolyhedraEditor

import BallCore.Groups.{GroupID, GroupManager, Position, Subclaims, SubgroupID}
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.*
import org.bukkit.Particle.DustOptions
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.{
    PlayerInteractEvent,
    PlayerMoveEvent,
    PlayerQuitEvent,
}
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin

import java.util
import java.util.Arrays
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.util.chaining.*

val brightRed = Color.fromRGB(0xe9, 0x3d, 0x58)
val dullRed = Color.fromRGB(108, 66, 72)
val brightBlue = Color.fromRGB(0x3d, 0xae, 0xe9)
val dullBlue = Color.fromRGB(82, 118, 137)
val draggingTeal = Color.fromRGB(0x00, 0xd4, 0x85)

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
                util.Arrays.asList(showingTo),
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

object PolyhedraEditor:
    def register()(using e: PolyhedraEditor, p: Plugin): Unit =
        p.getServer.getPluginManager.registerEvents(EditorListener(), p)

class EditorListener()(using e: PolyhedraEditor) extends Listener:

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

enum TargetedCorner:
    case cornerA // corner A is the bottom southeast corner
    case cornerB // corner B is the top northwest corner

enum VolumeEditingState:
    case none()
    case lookingAt(corner: TargetedCorner)
    case dragging(corner: TargetedCorner)

case class Volume(
    cornerA: Block,
    cornerB: Block,
):
    def cornerALocation(): Location =
        cornerA.getLocation().tap(_.add(0, 0, 0))

    def cornerBLocation(): Location =
        cornerB.getLocation().tap(_.add(1, 1, 1))

    private def coord1D(target: Double, a1: Double, a2: Double): Double =
        if !(a1 <= a2) then
            throw IllegalArgumentException("a1 must be lower than a2")

        if target < a1 then a1
        else if a1 <= target && target <= a2 then target
        else if target > a2 then a2
        else throw Exception(s"unhandled case $target $a1 $a2")

    private def square(x: Double): Double =
        x * x

    private def closestPointTo(target: Location): (Double, Double, Double) =
        val ca = cornerALocation()
        val cb = cornerBLocation()

        val ix = coord1D(target.getX, ca.getX, cb.getX)
        val ygriega = coord1D(target.getY, ca.getY, cb.getY)
        val zeta = coord1D(target.getZ, ca.getZ, cb.getZ)
        (ix, ygriega, zeta)

    def distanceTo(target: Location): Double =
        val (ix, ygriega, zeta) = closestPointTo(target)
        Math.sqrt(
            square(ix - target.getX) + square(ygriega - target.getY) + square(
                zeta - target.getZ
            )
        )

    def getTargetedCornerAndDistance(
        target: Location
    ): (TargetedCorner, Double) =
        val cornerADistance = cornerALocation().distance(target)
        val cornerBDistance = cornerBLocation().distance(target)
        if cornerADistance < cornerBDistance then
            (TargetedCorner.cornerA, cornerADistance)
        else (TargetedCorner.cornerB, cornerBDistance)

    def render(player: Player, color: Color): Unit =
        val cornerA = cornerALocation()
        val cornerB = cornerBLocation()

        // draw corner A lines
        drawLine(
            cornerA,
            cornerA.clone().tap(_.setX(cornerB.getX)),
            player,
            color,
            0.5,
        )
        drawLine(
            cornerA,
            cornerA.clone().tap(_.setY(cornerB.getY)),
            player,
            color,
            0.5,
        )
        drawLine(
            cornerA,
            cornerA.clone().tap(_.setZ(cornerB.getZ)),
            player,
            color,
            0.5,
        )

        // draw corner B lines
        drawLine(
            cornerB,
            cornerB.clone().tap(_.setX(cornerA.getX)),
            player,
            color,
            0.5,
        )
        drawLine(
            cornerB,
            cornerB.clone().tap(_.setY(cornerA.getY)),
            player,
            color,
            0.5,
        )
        drawLine(
            cornerB,
            cornerB.clone().tap(_.setZ(cornerA.getZ)),
            player,
            color,
            0.5,
        )

        // draw remaining vertical lines
        drawLine(
            cornerB.clone().tap(_.setX(cornerA.getX)),
            cornerA.clone().tap(_.setZ(cornerB.getZ)),
            player,
            color,
            0.5,
        )
        drawLine(
            cornerB.clone().tap(_.setZ(cornerA.getZ)),
            cornerA.clone().tap(_.setX(cornerB.getX)),
            player,
            color,
            0.5,
        )

        // draw lower horizontal lines
        drawLine(
            cornerB.clone().tap(_.setZ(cornerA.getZ)).tap(_.setY(cornerA.getY)),
            cornerB.clone().tap(_.setY(cornerA.getY)),
            player,
            color,
            0.5,
        )
        drawLine(
            cornerB.clone().tap(_.setX(cornerA.getX)).tap(_.setY(cornerA.getY)),
            cornerB.clone().tap(_.setY(cornerA.getY)),
            player,
            color,
            0.5,
        )

        // draw upper horizontal lines
        drawLine(
            cornerA.clone().tap(_.setZ(cornerB.getZ)).tap(_.setY(cornerB.getY)),
            cornerA.clone().tap(_.setY(cornerB.getY)),
            player,
            color,
            0.5,
        )
        drawLine(
            cornerA.clone().tap(_.setX(cornerB.getX)).tap(_.setY(cornerB.getY)),
            cornerA.clone().tap(_.setY(cornerB.getY)),
            player,
            color,
            0.5,
        )

case class EditingVolume(
    volume: Volume,
    state: VolumeEditingState,
):

    import VolumeEditingState.*

    def click(): EditingVolume =
        state match
            case lookingAt(it) =>
                this.copy(state = dragging(it))
            case dragging(it) =>
                this.copy(state = lookingAt(it))
            case _ =>
                this

    def look(targetLocation: Location, targetBlock: Block): EditingVolume =
        state match
            case dragging(TargetedCorner.cornerA) =>
                val targetBlockLocation = targetBlock.getLocation()
                val locA = volume.cornerA.getLocation()
                val locB = volume.cornerB.getLocation()
                locA.setX(targetBlockLocation.getX.min(locB.getX))
                locA.setY(targetBlockLocation.getY.min(locB.getY))
                locA.setZ(targetBlockLocation.getZ.min(locB.getZ))
                this.copy(volume = volume.copy(cornerA = locA.getBlock))
            case dragging(TargetedCorner.cornerB) =>
                val targetBlockLocation = targetBlock.getLocation()
                val locB = volume.cornerB.getLocation()
                val locA = volume.cornerA.getLocation()
                locB.setX(targetBlockLocation.getX.max(locA.getX))
                locB.setY(targetBlockLocation.getY.max(locA.getY))
                locB.setZ(targetBlockLocation.getZ.max(locA.getZ))
                this.copy(volume = volume.copy(cornerB = locB.getBlock))
            case _ =>
                this.copy(state =
                    Some(volume.getTargetedCornerAndDistance(targetLocation))
                        .filter(_._2 <= 1.1)
                        .map(x => lookingAt(x._1))
                        .getOrElse(none())
                )

    def render(player: Player): Unit =
        val cornerA = volume.cornerALocation()
        val cornerB = volume.cornerBLocation()

        val cornerAColor =
            if state == dragging(TargetedCorner.cornerA) then draggingTeal
            else if state == lookingAt(TargetedCorner.cornerA) then brightRed
            else dullRed

        val cornerBColor =
            if state == lookingAt(TargetedCorner.cornerB) then draggingTeal
            else if state == lookingAt(TargetedCorner.cornerB) then brightBlue
            else dullBlue

        // draw corner A corner caps
        drawLine(
            cornerA,
            cornerA.clone().tap(_.add(1, 0, 0)),
            player,
            cornerAColor,
            0.5,
        )
        drawLine(
            cornerA,
            cornerA.clone().tap(_.add(0, 1, 0)),
            player,
            cornerAColor,
            0.5,
        )
        drawLine(
            cornerA,
            cornerA.clone().tap(_.add(0, 0, 1)),
            player,
            cornerAColor,
            0.5,
        )

        // draw corner A sides
        drawLine(
            cornerA.clone().tap(_.add(1, 0, 0)),
            cornerA.clone().tap(_.setX(cornerB.getX)),
            player,
            Color.SILVER,
            0.5,
        )
        drawLine(
            cornerA.clone().tap(_.add(0, 1, 0)),
            cornerA.clone().tap(_.setY(cornerB.getY)),
            player,
            Color.SILVER,
            0.5,
        )
        drawLine(
            cornerA.clone().tap(_.add(0, 0, 1)),
            cornerA.clone().tap(_.setZ(cornerB.getZ)),
            player,
            Color.SILVER,
            0.5,
        )

        // draw corner B corner caps
        drawLine(
            cornerB,
            cornerB.clone().tap(_.add(-1, 0, 0)),
            player,
            cornerBColor,
            0.5,
        )
        drawLine(
            cornerB,
            cornerB.clone().tap(_.add(0, -1, 0)),
            player,
            cornerBColor,
            0.5,
        )
        drawLine(
            cornerB,
            cornerB.clone().tap(_.add(0, 0, -1)),
            player,
            cornerBColor,
            0.5,
        )

        // draw corner B sides
        drawLine(
            cornerB.clone().tap(_.add(-1, 0, 0)),
            cornerB.clone().tap(_.setX(cornerA.getX)),
            player,
            Color.SILVER,
            0.5,
        )
        drawLine(
            cornerB.clone().tap(_.add(0, -1, 0)),
            cornerB.clone().tap(_.setY(cornerA.getY)),
            player,
            Color.SILVER,
            0.5,
        )
        drawLine(
            cornerB.clone().tap(_.add(0, 0, -1)),
            cornerB.clone().tap(_.setZ(cornerA.getZ)),
            player,
            Color.SILVER,
            0.5,
        )

        // draw remaining vertical lines
        drawLine(
            cornerB.clone().tap(_.setX(cornerA.getX)),
            cornerA.clone().tap(_.setZ(cornerB.getZ)),
            player,
            Color.SILVER,
            0.5,
        )
        drawLine(
            cornerB.clone().tap(_.setZ(cornerA.getZ)),
            cornerA.clone().tap(_.setX(cornerB.getX)),
            player,
            Color.SILVER,
            0.5,
        )
        // draw lower horizontal lines
        drawLine(
            cornerB.clone().tap(_.setZ(cornerA.getZ)).tap(_.setY(cornerA.getY)),
            cornerB.clone().tap(_.setY(cornerA.getY)),
            player,
            Color.SILVER,
            0.5,
        )
        drawLine(
            cornerB.clone().tap(_.setX(cornerA.getX)).tap(_.setY(cornerA.getY)),
            cornerB.clone().tap(_.setY(cornerA.getY)),
            player,
            Color.SILVER,
            0.5,
        )
        // draw upper horizontal lines
        drawLine(
            cornerA.clone().tap(_.setZ(cornerB.getZ)).tap(_.setY(cornerB.getY)),
            cornerA.clone().tap(_.setY(cornerB.getY)),
            player,
            Color.SILVER,
            0.5,
        )
        drawLine(
            cornerA.clone().tap(_.setX(cornerB.getX)).tap(_.setY(cornerB.getY)),
            cornerA.clone().tap(_.setY(cornerB.getY)),
            player,
            Color.SILVER,
            0.5,
        )

enum EditingState:
    case nothing()
    case lookingAt(volumeIndex: Int)
    case editing(volume: EditingVolume)

case class State(
    volumes: List[Volume],
    state: EditingState,
    group: GroupID,
    subgroup: SubgroupID,
):

    import EditingState.*

    def done(): List[Volume] =
        state match
            case editing(volume) =>
                volume.volume :: volumes
            case _ =>
                volumes

    def leftClick(block: Block): State =
        state match
            case nothing() =>
                this
            case lookingAt(volumeIndex) =>
                val newVolumes = volumes.patch(volumeIndex, Nil, 1)
                copy(volumes = newVolumes, state = nothing())
            case editing(volume) =>
                copy(volumes = volume.volume :: volumes, state = nothing())

    def click(block: Block): State =
        state match
            case lookingAt(volumeIndex) =>
                val volume = volumes(volumeIndex)
                val newVolumes = volumes.patch(volumeIndex, Nil, 1)
                copy(
                    volumes = newVolumes,
                    state = editing(
                        EditingVolume(volume, VolumeEditingState.none())
                    ),
                )
            case editing(editingVolume) =>
                copy(state = editing(editingVolume.click()))
            case nothing() =>
                copy(volumes = Volume(block, block) :: volumes)

    def look(targetLocation: Location, targetBlock: Block): State =
        state match
            case editing(editingVolume) =>
                copy(state =
                    editing(editingVolume.look(targetLocation, targetBlock))
                )
            case _ =>
                volumes.zipWithIndex
                    .map { (volume, idx) =>
                        (volume.distanceTo(targetLocation), idx)
                    }
                    .filter(_._1 < 0.5)
                    .sortBy(_._1)
                    .map(_._2)
                    .lastOption match
                    case None =>
                        copy(state = nothing())
                    case Some(index) =>
                        copy(state = lookingAt(index))

    def render(player: Player): Unit =
        state match
            case nothing() =>
                volumes.foreach { volume =>
                    volume.render(player, Color.SILVER)
                }
            case lookingAt(volumeIndex) =>
                volumes.zipWithIndex.foreach { (volume, idx) =>
                    if idx == volumeIndex then
                        volume.render(player, draggingTeal)
                    else volume.render(player, Color.SILVER)
                }
            case editing(editingVolume) =>
                volumes.foreach { volume =>
                    volume.render(player, Color.GRAY)
                }
                editingVolume.render(player)

    def actionToolBar(player: Player): Unit =
        state match
            case nothing() =>
                player.sendActionBar(
                    txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Place a rectangle  |  ${txt("/done")
                            .style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Save and stop editing"
                )
            case lookingAt(volumeIndex) =>
                player.sendActionBar(
                    txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Resize this rectangle  |  ${keybind("key.attack")
                            .style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Delete this rectangle"
                )
            case editing(editingState) =>
                editingState.state match
                    case VolumeEditingState.none() =>
                        player.sendActionBar(
                            txt"${keybind("key.attack").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Stop resizing this rectangle"
                        )
                    case BallCore.PolyhedraEditor.VolumeEditingState
                            .lookingAt(corner) =>
                        player.sendActionBar(
                            txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Start dragging this corner  |  ${keybind("key.attack")
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Stop resizing this rectangle"
                        )
                    case VolumeEditingState.dragging(corner) =>
                        player.sendActionBar(
                            txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Stop dragging this corner"
                        )

// notes: block locations are the center of the top face

class PolyhedraEditor(using p: Plugin, sql: SQLManager, gm: GroupManager):
    private val playerPolygons = TrieMap[Player, State]()

    p.getServer.getAsyncScheduler
        .runAtFixedRate(
            p,
            t => render(),
            1 * 50,
            10 * 50,
            TimeUnit.MILLISECONDS,
        )

    def leave(player: Player): Unit =
        val _ = playerPolygons.remove(player)

    def create(
        player: Player,
        world: World,
        group: GroupID,
        subgroup: SubgroupID,
    ): Unit =
        sql.useBlocking(sql.withTX(gm.getSubclaims(group))) match
            case Left(err) =>
                player.sendServerMessage(
                    txt"I could not start the subclaim editor because ${err.explain()}"
                )
            case Right(claims) =>
                val volumes =
                    claims.getOrElse(subgroup, Subclaims(Nil)).volumes.map {
                        volume =>
                            def into(ca: Position): Block =
                                Bukkit
                                    .getWorld(ca.world)
                                    .getBlockAt(ca.x, ca.y, ca.z)

                            Volume(into(volume.cornerA), into(volume.cornerB))
                    }
                playerPolygons(player) =
                    State(volumes, EditingState.nothing(), group, subgroup)

    def render(): Unit =
        playerPolygons.foreach { (player, model) =>
            player.getScheduler
                .run(
                    p,
                    _ => {
                        model.render(player)
                        model.actionToolBar(player)
                    },
                    null,
                )
        }

    def done(player: Player): Unit =
        val state = playerPolygons.get(player) match
            case None => return
            case Some(value) => value

        val volumes = state.done().map { volume =>
            def into(ca: Block): Position =
                Position(ca.getX, ca.getY, ca.getZ, ca.getWorld.getUID)

            BallCore.Groups.Volume(into(volume.cornerA), into(volume.cornerB))
        }

        sql.useBlocking(
            sql.withTX(
                gm.setSubclaims(
                    player.getUniqueId,
                    state.group,
                    state.subgroup,
                    Subclaims(volumes),
                )
            )
        ) match
            case Left(err) =>
                player.sendServerMessage(
                    txt"I could not save the claims because ${err.explain()}"
                )
            case Right(_) =>
                player.sendServerMessage(
                    txt"Claims have been successfully updated!"
                )
                playerPolygons.remove(player, state)
                ()

    def leftClicked(player: Player, on: Block): Boolean =
        playerPolygons
            .updateWith(player) {
                _.map { state =>
                    state.leftClick(on)
                }
            }
            .isDefined

    def clicked(player: Player, on: Block): Boolean =
        playerPolygons
            .updateWith(player) {
                _.map { state =>
                    state.click(on)
                }
            }
            .isDefined

    def look(
        player: Player,
        targetLocation: Location,
        targetBlock: Block,
    ): Unit =
        playerPolygons.updateWith(player) {
            _.map { state =>
                state.look(targetLocation, targetBlock)
            }
        }
        ()
