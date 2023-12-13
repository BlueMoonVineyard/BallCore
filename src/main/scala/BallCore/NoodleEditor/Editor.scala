package BallCore.NoodleEditor

import org.bukkit.Location
import org.bukkit.entity.Player
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
import BallCore.PolygonEditor.LineDrawer
import scala.collection.concurrent.TrieMap
import scalax.collection.edges._
import scalax.collection.immutable.Graph
import scala.util.chaining._
import BallCore.PolygonEditor.LineColour

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

enum State:
    case noPoints
    case onePoint(previous: Location)
    case idle(lookingAt: Option[Location])
    case dragging(location: Location)
    case creatingFrom(location: Location)

def round(l: Location): Location =
    Location(l.getWorld, l.blockX + 0.5d, l.blockY, l.blockZ + 0.5d)

case class PlayerState(
    lineDrawer: LineDrawer,
    graph: Graph[Location, UnDiEdge[Location]],
    target: Location,
    state: State,
):
    def leave(): Unit =
        lineDrawer.clear()

    def render(): Unit =
        state match
            case State.noPoints =>
            case State.onePoint(previous) =>
            case State.idle(lookingAt) =>
                val nodeHandles = graph.nodes.toList.map { node =>
                    val loc: Location = node
                    (
                        loc,
                        loc.clone().tap(_.add(0, 1, 0)),
                        if Some(loc) == lookingAt then LineColour.lime
                        else LineColour.gray,
                    )
                }
                val edges = graph.edges.toList.map { edge =>
                    val from: Location = edge._1
                    val to: Location = edge._2
                    (from, to, LineColour.white)
                }
                lineDrawer.setLines(nodeHandles.concat(edges))
            case State.dragging(dragging) =>
                val nodeHandles = graph.nodes.toList.map { node =>
                    val loc: Location = node
                    (
                        loc,
                        loc.clone().tap(_.add(0, 1, 0)),
                        if Some(loc) == dragging then LineColour.teal
                        else LineColour.gray,
                    )
                }
                val edges = graph.edges.toList.map { edge =>
                    val from: Location = edge._1
                    val to: Location = edge._2
                    (from, to, LineColour.white)
                }
                lineDrawer.setLines(nodeHandles.concat(edges))
            case State.creatingFrom(origin) =>
                val nodeHandles = graph.nodes.toList.map { node =>
                    val loc: Location = node
                    (
                        loc,
                        loc.clone().tap(_.add(0, 1, 0)),
                        LineColour.gray,
                    )
                }
                val edges = graph.edges.toList.map { edge =>
                    val from: Location = edge._1
                    val to: Location = edge._2
                    (from, to, LineColour.white)
                }
                val newLines = List(
                    (origin, target, LineColour.white),
                    (target, target.clone().tap(_.add(0, 1, 0)), LineColour.teal),
                )
                lineDrawer.setLines(nodeHandles.concat(edges).concat(newLines))

    def look(l: Location): PlayerState =
        state match
            case State.noPoints =>
                copy(target = round(l))
            case State.onePoint(previous) =>
                copy(target = round(l))
            case State.idle(_) =>
                copy(state =
                    State.idle(
                        graph.nodes.toList
                            .sortBy(_.distance(l))
                            .headOption
                            .filter(_.distance(l) < 2)
                            .map(x => x: Location)
                    )
                )
            case State.dragging(location) =>
                copy(
                    graph = graph.map((node: graph.NodeT) =>
                        if node == location then round(l) else node
                    ),
                    state = State.dragging(round(l)),
                )
            case State.creatingFrom(location) =>
                copy(target = round(l))

    def click(sneaking: Boolean): PlayerState =
        state match
            case State.noPoints =>
                copy(state = State.onePoint(target))
            case State.onePoint(previous) =>
                copy(
                    state = State.idle(None),
                    graph = graph + previous + target + previous ~ target,
                )
            case State.idle(Some(x)) =>
                if sneaking then copy(state = State.dragging(x))
                else copy(state = State.creatingFrom(x))
            case State.idle(None) =>
                this
            case State.dragging(_) =>
                copy(state = State.idle(None))
            case State.creatingFrom(origin) =>
                copy(state = State.idle(None), graph = graph + target + origin ~ target)

    def leftClick(): PlayerState =
        this

object NoodleEditor:
    def register()(using p: Plugin): NoodleEditor =
        given NoodleEditor = NoodleEditor()
        p.getServer().getPluginManager().registerEvents(EditorListener(), p)
        summon[NoodleEditor]

class NoodleEditor(using p: Plugin):
    private val states = TrieMap[Player, PlayerState]()

    def create(p: Player): Unit =
        states(p) = PlayerState(
            LineDrawer(p, org.bukkit.util.Vector()),
            Graph.empty,
            (p.getLocation()),
            State.noPoints,
        )

    def done(p: Player): Unit =
        ???

    def cancel(p: Player): Unit =
        states.remove(p).foreach(_.leave())

    def leave(p: Player): Unit =
        states.remove(p).foreach(_.leave())

    def look(p: Player, l: Location, b: Block): Unit =
        states.updateWith(p)(_.map(_.look(l))).foreach(_.render())

    def clicked(p: Player, b: Block): Boolean =
        states
            .updateWith(p)(_.map(_.click(p.isSneaking)))
            .tap(_.foreach(_.render()))
            .isDefined

    def leftClicked(p: Player, b: Block): Boolean =
        states
            .updateWith(p)(_.map(_.leftClick()))
            .tap(_.foreach(_.render()))
            .isDefined
