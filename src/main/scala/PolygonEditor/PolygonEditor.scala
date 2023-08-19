package BallCore.PolygonEditor

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import scala.concurrent.Future
import org.bukkit.Location
import scala.concurrent.Promise
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Particle
import java.{util => ju}
import java.util.Arrays
import org.bukkit.Particle.DustOptions
import org.bukkit.Color
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import scala.collection.concurrent.TrieMap
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot
import java.util.concurrent.TimeUnit
import org.locationtech.jts.geom.Coordinate
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import BallCore.Beacons.BeaconID
import BallCore.Beacons.CivBeaconManager
import org.bukkit.World

object PolygonEditor:
	def register()(using e: PolygonEditor, p: Plugin): Unit =
		p.getServer().getPluginManager().registerEvents(EditorListener(), p)

class EditorListener()(using e: PolygonEditor) extends Listener:
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	def interact(event: PlayerInteractEvent): Unit =
		if event.getHand() != EquipmentSlot.HAND then
			return

		event.getAction() match
			case Action.RIGHT_CLICK_BLOCK =>
				if e.clicked(event.getPlayer(), event.getClickedBlock().getLocation()) then
					event.setCancelled(true)
			case Action.LEFT_CLICK_BLOCK =>
				if e.leftClicked(event.getPlayer(), event.getClickedBlock().getLocation()) then
					event.setCancelled(true)
			case _ =>

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	def look(event: PlayerMoveEvent): Unit =
		val targetBlock = event.getPlayer().getTargetBlockExact(30)
		if targetBlock == null then
			return
		val targetLoc = targetBlock.getLocation()

		e.look(event.getPlayer(), targetLoc)

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	def quit(event: PlayerQuitEvent): Unit =
		e.leave(event.getPlayer())

enum PlayerState:
	case editing(state: EditorModel)
	case creating(state: CreatorModel, actions: List[CreatorAction])

class PolygonEditor(using p: Plugin, bm: CivBeaconManager):
	private val clickPromises = TrieMap[Player, Promise[Location]]()
	private val playerPolygons = TrieMap[Player, PlayerState]()

	p.getServer().getAsyncScheduler().runAtFixedRate(p, t => render(), 1 * 50, 10 * 50, TimeUnit.MILLISECONDS)

	def leave(player: Player): Unit =
		val _ = playerPolygons.remove(player)

	def create(player: Player, world: World, beaconID: BeaconID): Unit =
		import BallCore.UI.ChatElements._
		bm.getPolygonFor(beaconID) match
			case None =>
				player.sendMessage(txt"You'll be defining your claim as 4 points")
				player.sendMessage(txt"Press ${keybind("key.use")} to place Point 1")
				playerPolygons(player) = PlayerState.creating(CreatorModel(beaconID, CreatorModelState.definingPointA()), List())
			case Some(polygon) =>
				player.sendMessage(txt"Press ${keybind("key.use")} to pick up and to place claim points.")
				player.sendMessage(txt"Press ${keybind("key.use")} on a midpoint to create a new point from it, and press ${keybind("key.attack")} on a point to delete it.")
				playerPolygons(player) = PlayerState.editing(EditorModel(beaconID, polygon, world))

	def render(): Unit =
		playerPolygons.foreach { (player, model) =>
			player.getScheduler().run(p, _ => {
				model match
					case PlayerState.editing(state) =>
						renderEditor(player, state)
					case PlayerState.creating(state, actions) =>
						renderCreator(player, state, actions)
			}, null)
		}
	def renderCreator(player: Player, model: CreatorModel, actions: List[CreatorAction]): Unit =
		actions.foreach { action =>
			action match
				case CreatorAction.drawLine(from, to) =>
					drawLine(from, to, player, Color.WHITE, 0.5)
				case CreatorAction.drawSelectionLine(from) =>
					val to = player.getTargetBlockExact(100).getLocation()
					drawLine(from, to, player, Color.AQUA, 0.5)
				case CreatorAction.drawFinisherLine(to) =>
					val from = player.getTargetBlockExact(100).getLocation()
					drawLine(from, to, player, Color.fromRGB(0xA8ABB0), 1.0)
				case CreatorAction.finished(_) =>
				case CreatorAction.notifyPlayer(_) =>
		}
	def renderEditor(player: Player, model: EditorModel): Unit =
		import EditorModelState._

		model.lines.foreach { (p1, p2) =>
			drawLine(p1, p2, player, Color.WHITE, 0.5)
		}
		model.polygon.zipWithIndex.foreach { (point, idx) =>
			val colour =
				if model.state == lookingAt(point) then
					Color.TEAL
				else if model.state == editingPoint(idx, false) then
					Color.RED
				else if model.state == editingPoint(idx, true) then
					Color.YELLOW
				else
					Color.ORANGE
			drawLine(point, point.clone().add(0, 2, 0), player, colour, 0.5)
		}
		model.midpoints.foreach { (_, point) =>
			val colour =
				if model.state == lookingAt(point) then
					Color.TEAL
				else
					Color.LIME
			drawLine(point, point.clone().add(0, 2, 0), player, colour, 0.5)
		}
	def handleEditor(player: Player, model: EditorModel, actions: List[EditorAction]): Option[PlayerState] =
		val done = actions.filter { action =>
			action match
				case EditorAction.notifyPlayer(msg) =>
					player.sendMessage(msg)
					false
				case _ =>
					true
		}.flatMap { action =>
			action match
				case EditorAction.finished() =>
					val jtsPolygon = gf.createPolygon(
						model.polygon.appended(model.polygon.head)
							.map(point => Coordinate(point.getX(), point.getZ(), point.getY()))
							.toArray
					)
					bm.updateBeaconPolygon(model.beaconID, model.polygon.head.getWorld(), jtsPolygon) match
						case Left(err) =>
							player.sendMessage(err.explain)
							None
						case Right(_) =>
							Some(())
				case _ =>
					None
		}
		done.lastOption match
			case None =>
				Some(PlayerState.editing(model))
			case Some(_) =>
				None
	def handleCreator(player: Player, model: CreatorModel, actions: List[CreatorAction]): PlayerState =
		val done = actions.filter { action =>
			action match
				case CreatorAction.notifyPlayer(message) =>
					player.sendMessage(message)
					false
				case _ =>
					true
		}.flatMap { action =>
			action match
				case CreatorAction.finished(points) =>
					Some(PlayerState.editing(EditorModel(model.beaconID, points)))
				case _ =>
					None
		}
		done.lastOption match
			case None =>
				PlayerState.creating(model, actions)
			case Some(state) =>
				state
	def done(player: Player): Unit =
		val _ = playerPolygons.updateWith(player) { state =>
			state.flatMap(state => state match
				case PlayerState.editing(state) =>
					val (model, actions) = state.update(EditorMsg.done())
					handleEditor(player, model, actions)
				case _ =>
					Some(state)
			)
		}
	def leftClicked(player: Player, on: Location): Boolean =
		if playerPolygons.contains(player) then
			playerPolygons.updateWith(player) { state =>
				state.flatMap(state => state match
					case PlayerState.editing(state) =>
						val (model, actions) = state.update(EditorMsg.leftClick())
						handleEditor(player, model, actions)
					case _ =>
						Some(state)
				)
			}
			true
		else
			false
	def clicked(player: Player, on: Location): Boolean =
		if clickPromises.contains(player) then
			clickPromises.get(player).map(_.success(on))
			clickPromises.remove(player)
			true
		else if playerPolygons.contains(player) then
			val _ = playerPolygons.updateWith(player) { state =>
				state.flatMap(state => state match
					case PlayerState.editing(state) =>
						val (model, actions) = state.update(EditorMsg.rightClick())
						handleEditor(player, model, actions)
					case PlayerState.creating(state, _) =>
						val (model, actions) = state.update(CreatorMsg.click(on))
						Some(handleCreator(player, model, actions))
				)
			}
			true
		else
			false
	def look(player: Player, targetLoc: Location): Unit =
		if !playerPolygons.contains(player) then
			return

		val _ = playerPolygons.updateWith(player) { state =>
			state.flatMap(state => state match
				case PlayerState.editing(state) =>
					val (model, actions) = state.update(EditorMsg.look(targetLoc))
					handleEditor(player, model, actions)
				case _ =>
					Some(state)
			)
		}

	def getClick(player: Player): Future[Location] =
		clickPromises.get(player).map(_.failure(null))
		clickPromises.remove(player)
		val prom = Promise[Location]()
		clickPromises(player) = prom
		prom.future
	def drawLine(fromBlock: Location, toBlock: Location, showingTo: Player, color: Color, detail: Double): Unit =
		val start = fromBlock.clone().add(0.5, 1.0, 0.5)
		val finish = toBlock.clone().add(0.5, 1.0, 0.5)

		val dir = finish.toVector().subtract(start.toVector()).normalize()
		val len = start.distance(finish)

		for i <- Iterator.iterate(0.0)(_ + detail).takeWhile(_ < len) do
			val offset = dir.clone().multiply(i)
			val pos = start.clone().add(offset)

			pos.getWorld().spawnParticle(
				Particle.REDSTONE,
				Arrays.asList(showingTo),
				showingTo,
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				4,
				0.0,
				0.0,
				0.0,
				0.0,
				DustOptions(color, 1.0),
			)