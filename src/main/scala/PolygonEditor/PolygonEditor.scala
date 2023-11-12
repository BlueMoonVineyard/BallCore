// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.PolygonEditor

import BallCore.Beacons.{BeaconID, CivBeaconManager}
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.{PlayerInteractEvent, PlayerMoveEvent, PlayerQuitEvent}
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin
import org.bukkit.{Color, Location, Particle, World}
import org.locationtech.jts.geom.Coordinate

import java.util as ju
import java.util.Arrays
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}

object PolygonEditor:
  def register()(using e: PolygonEditor, p: Plugin): Unit =
    p.getServer().getPluginManager().registerEvents(EditorListener(), p)

class EditorListener()(using e: PolygonEditor) extends Listener:
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  def interact(event: PlayerInteractEvent): Unit =
    if event.getHand() != EquipmentSlot.HAND then return

      event.getAction() match
        case Action.RIGHT_CLICK_BLOCK =>
          if e.clicked(event.getPlayer(), event.getClickedBlock().getLocation())
          then event.setCancelled(true)
        case Action.LEFT_CLICK_BLOCK =>
          if e.leftClicked(
            event.getPlayer(),
            event.getClickedBlock().getLocation()
          )
          then event.setCancelled(true)
        case _ =>

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  def look(event: PlayerMoveEvent): Unit =
    val targetBlock = event.getPlayer().getTargetBlockExact(30)
    if targetBlock == null then return
    val targetLoc = targetBlock.getLocation()

    e.look(event.getPlayer(), targetLoc)

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  def quit(event: PlayerQuitEvent): Unit =
    e.leave(event.getPlayer())

enum PlayerState:
  case editing(state: EditorModel)
  case creating(state: CreatorModel, actions: List[CreatorAction])

class PolygonEditor(using p: Plugin, bm: CivBeaconManager, sql: SQLManager):
  private val clickPromises = TrieMap[Player, Promise[Location]]()
  private val playerPolygons = TrieMap[Player, PlayerState]()

  p.getServer()
    .getAsyncScheduler()
    .runAtFixedRate(p, t => render(), 1 * 50, 10 * 50, TimeUnit.MILLISECONDS)

  def leave(player: Player): Unit =
    val _ = playerPolygons.remove(player)

  def create(player: Player, world: World, beaconID: BeaconID): Unit =
    import BallCore.UI.ChatElements.*
    sql.useBlocking(bm.getPolygonFor(beaconID)) match
      case None =>
        player.sendServerMessage(txt"You'll be defining your claim as 4 points")
        playerPolygons(player) = PlayerState.creating(
          CreatorModel(beaconID, CreatorModelState.definingPointA()),
          List()
        )
      case Some(polygon) =>
        player.sendServerMessage(txt"You've started editing your claims")
        playerPolygons(player) =
          PlayerState.editing(EditorModel(beaconID, polygon, world))

  def render(): Unit =
    playerPolygons.foreach { (player, model) =>
      player
        .getScheduler()
        .run(
          p,
          _ => {
            model match
              case PlayerState.editing(state) =>
                renderEditor(player, state)
              case PlayerState.creating(state, actions) =>
                renderCreator(player, state, actions)
          },
          null
        )
    }

  def renderCreator(
                     player: Player,
                     model: CreatorModel,
                     actions: List[CreatorAction]
                   ): Unit =
    actions.foreach { action =>
      action match
        case CreatorAction.drawLine(from, to) =>
          drawLine(from, to, player, Color.WHITE, 0.5)
        case CreatorAction.drawSelectionLine(from) =>
          val to = player.getTargetBlockExact(100).getLocation()
          drawLine(from, to, player, Color.AQUA, 0.5)
        case CreatorAction.drawFinisherLine(to) =>
          val from = player.getTargetBlockExact(100).getLocation()
          drawLine(from, to, player, Color.fromRGB(0xa8abb0), 1.0)
        case CreatorAction.finished(_) =>
        case CreatorAction.notifyPlayer(_) =>
    }
    model.state match
      case CreatorModelState.definingPointA() =>
        player.sendActionBar(
          txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Place Point 1"
        )
      case CreatorModelState.definingPointB(pointA) =>
        player.sendActionBar(
          txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Place Point 2"
        )
      case CreatorModelState.definingPointC(pointA, pointB) =>
        player.sendActionBar(
          txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Place Point 3"
        )
      case CreatorModelState.definingPointD(pointA, pointB, pointC) =>
        player.sendActionBar(
          txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Place Point 4"
        )

  def renderEditor(player: Player, model: EditorModel): Unit =
    import EditorModelState.*

    model.lines.foreach { (p1, p2) =>
      drawLine(p1, p2, player, Color.WHITE, 0.5)
    }
    model.polygon.zipWithIndex.foreach { (point, idx) =>
      val colour =
        if model.state == lookingAt(point) then Color.TEAL
        else if model.state == editingPoint(idx, false) then Color.RED
        else if model.state == editingPoint(idx, true) then Color.YELLOW
        else Color.ORANGE
      drawLine(point, point.clone().add(0, 2, 0), player, colour, 0.5)
    }
    model.midpoints.foreach { (_, point) =>
      val colour =
        if model.state == lookingAt(point) then Color.TEAL
        else Color.LIME
      drawLine(point, point.clone().add(0, 2, 0), player, colour, 0.5)
    }
    model.state match
      case idle() =>
        player.sendActionBar(
          txt"${txt("/done").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Save and stop editing"
        )
      case lookingAt(loc) =>
        player.sendActionBar(
          txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Start dragging this point  |  ${
            keybind("key.attack")
              .style(NamedTextColor.GOLD, TextDecoration.BOLD)
          }: Delete this point"
        )
      case editingPoint(idx, valid) =>
        player.sendActionBar(
          txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Stop dragging this point"
        )

  def handleEditor(
                    player: Player,
                    model: EditorModel,
                    actions: List[EditorAction]
                  ): Option[PlayerState] =
    val done = actions
      .filter { action =>
        action match
          case EditorAction.notifyPlayer(msg) =>
            player.sendServerMessage(msg)
            false
          case _ =>
            true
      }
      .flatMap { action =>
        action match
          case EditorAction.finished() =>
            val jtsPolygon = gf.createPolygon(
              model.polygon
                .appended(model.polygon.head)
                .map(point =>
                  Coordinate(point.getX(), point.getZ(), point.getY())
                )
                .toArray
            )
            sql.useBlocking(
              bm.updateBeaconPolygon(
                model.beaconID,
                model.polygon.head.getWorld(),
                jtsPolygon
              )
            ) match
              case Left(err) =>
                player.sendServerMessage(err.explain)
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

  def handleCreator(
                     player: Player,
                     model: CreatorModel,
                     actions: List[CreatorAction]
                   ): PlayerState =
    val done = actions
      .filter { action =>
        action match
          case CreatorAction.notifyPlayer(message) =>
            player.sendServerMessage(message)
            false
          case _ =>
            true
      }
      .flatMap { action =>
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
      state.flatMap(state =>
        state match
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
        state.flatMap(state =>
          state match
            case PlayerState.editing(state) =>
              val (model, actions) = state.update(EditorMsg.leftClick())
              handleEditor(player, model, actions)
            case _ =>
              Some(state)
        )
      }
      true
    else false

  def clicked(player: Player, on: Location): Boolean =
    if clickPromises.contains(player) then
      clickPromises.get(player).map(_.success(on))
      clickPromises.remove(player)
      true
    else if playerPolygons.contains(player) then
      val _ = playerPolygons.updateWith(player) { state =>
        state.flatMap(state =>
          state match
            case PlayerState.editing(state) =>
              val (model, actions) = state.update(EditorMsg.rightClick())
              handleEditor(player, model, actions)
            case PlayerState.creating(state, _) =>
              val (model, actions) = state.update(CreatorMsg.click(on))
              Some(handleCreator(player, model, actions))
        )
      }
      true
    else false

  def look(player: Player, targetLoc: Location): Unit =
    if !playerPolygons.contains(player) then return

    val _ = playerPolygons.updateWith(player) { state =>
      state.flatMap(state =>
        state match
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

  def drawLine(
                fromBlock: Location,
                toBlock: Location,
                showingTo: Player,
                color: Color,
                detail: Double
              ): Unit =
    val start = fromBlock.clone().add(0.5, 1.0, 0.5)
    val finish = toBlock.clone().add(0.5, 1.0, 0.5)

    val dir = finish.toVector().subtract(start.toVector()).normalize()
    val len = start.distance(finish)

    for i <- Iterator.iterate(0.0)(_ + detail).takeWhile(_ < len) do
      val offset = dir.clone().multiply(i)
      val pos = start.clone().add(offset)

      pos
        .getWorld()
        .spawnParticle(
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
          DustOptions(color, 1.0)
        )
