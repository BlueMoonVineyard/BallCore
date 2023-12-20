// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.PolygonEditor

import BallCore.Beacons.{BeaconID, CivBeaconManager}
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
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
import org.bukkit.{Location, World}
import org.locationtech.jts.geom.Coordinate

import java.util as ju
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}
import BallCore.Beacons.PolygonAdjustmentError
import BallCore.Sigils.BattleManager
import cats.effect.IO
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import BallCore.Sigils.BattleError
import org.locationtech.jts.geom.Polygon
import BallCore.Folia.EntityExecutionContext

object PolygonEditor:
    def register()(using e: PolygonEditor, p: Plugin): Unit =
        p.getServer.getPluginManager.registerEvents(EditorListener(), p)

class EditorListener()(using e: PolygonEditor) extends Listener:
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def interact(event: PlayerInteractEvent): Unit =
        if event.getHand != EquipmentSlot.HAND then return

        event.getAction match
            case Action.RIGHT_CLICK_BLOCK =>
                if e.clicked(
                        event.getPlayer,
                        event.getClickedBlock.getLocation(),
                    )
                then event.setCancelled(true)
            case Action.LEFT_CLICK_BLOCK =>
                if e.leftClicked(
                        event.getPlayer,
                        event.getClickedBlock.getLocation(),
                    )
                then event.setCancelled(true)
            case _ =>

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def look(event: PlayerMoveEvent): Unit =
        val targetBlock = event.getPlayer.getTargetBlockExact(30)
        if targetBlock == null then return
        val targetLoc = targetBlock.getLocation()

        e.look(event.getPlayer, targetLoc)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def quit(event: PlayerQuitEvent): Unit =
        e.leave(event.getPlayer)

enum PlayerState:
    case editing(
        state: EditorModel,
        bar: BossBar,
        maxArea: Int,
        lineDrawer: LineDrawer,
    )
    case creating(
        state: CreatorModel,
        actions: List[CreatorAction],
        maxArea: Int,
        lineDrawer: LineDrawer,
    )
    case viewing(
        beacons: List[(Polygon, World, BeaconID)],
        lineDrawer: LineDrawer,
    )

class PolygonEditor(using
    p: Plugin,
    bm: CivBeaconManager,
    sql: SQLManager,
    battleManager: BattleManager,
):
    private val clickPromises = TrieMap[Player, Promise[Location]]()
    private val playerPolygons = TrieMap[Player, PlayerState]()

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

    private def createBossBar(
        actualArea: Int,
        maxArea: Int,
        player: Player,
    ): BossBar =
        val bar = BossBar.bossBar(
            Component.text(s"Area Used: ${actualArea} / ${maxArea}"),
            (actualArea.toFloat / maxArea.toFloat).min(1f).max(0f),
            if actualArea > maxArea then BossBar.Color.RED
            else BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS,
        )
        bar.addViewer(player)
        bar

    private def updateBossBar(
        actualArea: Int,
        maxArea: Int,
        bar: BossBar,
    ): Unit =
        bar.name(Component.text(s"Area Used: ${actualArea} / ${maxArea}"))
        bar.color(
            if actualArea > maxArea then BossBar.Color.RED
            else BossBar.Color.GREEN
        )
        bar.progress((actualArea.toFloat / maxArea.toFloat).min(1f).max(0f))
        ()

    def view(player: Player, beacons: List[(Polygon, World, BeaconID)]): Unit =
        player.sendServerMessage(
            txt"You are looking at ${beacons.size} nearby claims within 32 blocks of you."
        )
        val lineDrawer = LineDrawer(player, org.bukkit.util.Vector(0.5, 1, 0.5))

        val lines = beacons.flatMap { case (polygon, world, _) =>
            val points =
                polygon.getCoordinates
                    .map(coord =>
                        Location(world, coord.getX, coord.getZ, coord.getY)
                    )
                    .toList
                    .dropRight(1)
            val lines =
                points
                    .sliding(2, 1)
                    .concat(List(List(points.last, points.head)))
                    .map(pair => (pair.head, pair(1)))
                    .toList
            lines.map((p1, p2) => (p1, p2, LineColour.orange))
        }
        sql.useFireAndForget(IO {
            lineDrawer.setLines(lines)
        }.evalOn(EntityExecutionContext(player)))

        playerPolygons(player) = PlayerState.viewing(beacons, lineDrawer)

    def create(player: Player, world: World, beaconID: BeaconID): Unit =
        import BallCore.UI.ChatElements.*
        sql.useBlocking(sql.withS((for {
            existingPolygon <- bm.getPolygonFor(beaconID)
            population <- bm.beaconSize(beaconID)
            area = CivBeaconManager.populationToArea(population.toInt)
        } yield existingPolygon match
            case None =>
                player.sendServerMessage(
                    txt"You'll be defining your claim as 4 points"
                )
                playerPolygons(player) = PlayerState.creating(
                    CreatorModel(beaconID, CreatorModelState.definingPointA()),
                    List(),
                    area,
                    LineDrawer(player, org.bukkit.util.Vector(0.5, 1, 0.5)),
                )
            case Some(polygon) =>
                player.sendServerMessage(
                    txt"You've started editing your claims"
                )
                val model = EditorModel(beaconID, polygon, world)
                val bar = createBossBar(polygon.getArea().toInt, area, player)
                val lineDrawer =
                    LineDrawer(player, org.bukkit.util.Vector(0.5, 1, 0.5))
                updateEditorPersistent(model, bar, area, lineDrawer)
                playerPolygons(player) = PlayerState.editing(
                    model,
                    bar,
                    area,
                    lineDrawer,
                )
        )))

    def render(): Unit =
        playerPolygons.foreach { (player, model) =>
            player.getScheduler
                .run(
                    p,
                    _ => {
                        model match
                            case PlayerState.editing(state, _, _, _) =>
                                renderEditor(player, state)
                            case PlayerState
                                    .creating(state, actions, _, lineDrawer) =>
                                renderCreator(
                                    player,
                                    state,
                                    actions,
                                    lineDrawer,
                                )
                            case PlayerState.viewing(_, _) =>
                                renderViewer(player)
                    },
                    null,
                )
        }

    private def renderViewer(
        player: Player
    ) =
        player.sendActionBar(
            txt"${txt("/done").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Stop viewing these claims"
        )

    private def updateCreatorPersistent(
        player: Player,
        actions: List[CreatorAction],
        lineDrawer: LineDrawer,
    ): Unit =
        lineDrawer.setLines(actions.flatMap {
            case CreatorAction.drawLine(from, to) =>
                Some((from, to, LineColour.white))
            case CreatorAction.drawSelectionLine(from) =>
                val to = player.getTargetBlockExact(100).getLocation()
                Some((from, to, LineColour.teal))
            case CreatorAction.drawFinisherLine(to) =>
                val from = player.getTargetBlockExact(100).getLocation()
                Some((from, to, LineColour.gray))
            case CreatorAction.finished(_) =>
                None
            case CreatorAction.notifyPlayer(_) =>
                None
        })

    private def renderCreator(
        player: Player,
        model: CreatorModel,
        actions: List[CreatorAction],
        lineDrawer: LineDrawer,
    ): Unit =
        updateCreatorPersistent(player, actions, lineDrawer)
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

    private def renderEditor(
        player: Player,
        model: EditorModel,
    ): Unit =
        import EditorModelState.*

        model.state match
            case idle() =>
                player.sendActionBar(
                    model.couldWarGroup match
                        case None =>
                            txt"${txt("/done").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Save and stop editing  |  ${txt("/cancel")
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        case Some(_) =>
                            txt"${txt("/done").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Save and stop editing  |  ${txt("/declare")
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Start a battle for this land  |  ${txt("/cancel")
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                )
            case lookingAt(loc) =>
                player.sendActionBar(
                    txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Start dragging this point  |  ${keybind("key.attack")
                            .style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Delete this point"
                )
            case editingPoint(idx, valid) =>
                player.sendActionBar(
                    txt"${keybind("key.use").style(NamedTextColor.GOLD, TextDecoration.BOLD)}: Stop dragging this point"
                )

    private def handleEditor(
        player: Player,
        model: EditorModel,
        actions: List[EditorAction],
        bar: BossBar,
        maxArea: Int,
        lineDrawer: LineDrawer,
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
            .map { action =>
                action match
                    case EditorAction.finished() =>
                        val jtsPolygon = gf.createPolygon(
                            model.polygon
                                .appended(model.polygon.head)
                                .map(point =>
                                    Coordinate(
                                        point.getX,
                                        point.getZ,
                                        point.getY,
                                    )
                                )
                                .toArray
                        )
                        sql.useBlocking(
                            sql.withS(
                                sql.withTX(
                                    bm.updateBeaconPolygon(
                                        model.beaconID,
                                        model.polygon.head.getWorld,
                                        jtsPolygon,
                                    )
                                )
                            )
                        ) match
                            case Left(
                                    err @ PolygonAdjustmentError
                                        .overlapsOneOtherPolygon(
                                            beacon,
                                            group,
                                            _,
                                            Some(polygon),
                                        )
                                ) =>
                                player.sendServerMessage(err.explain)
                                Some(
                                    model.copy(couldWarGroup =
                                        Some((group, beacon, polygon))
                                    )
                                )
                            case Left(err) =>
                                player.sendServerMessage(err.explain)
                                Some(model.copy(couldWarGroup = None))
                            case Right(_) =>
                                None
                    case _ =>
                        Some(model)
            }
        val newModel = done.lastOption match
            case None =>
                Some(model)
            case Some(None) =>
                bar.removeViewer(player)
                lineDrawer.clear()
                None
            case Some(Some(newModel)) =>
                Some(newModel)
        newModel.foreach { model =>
            updateEditorPersistent(model, bar, maxArea, lineDrawer)
        }
        newModel.map(model =>
            PlayerState.editing(model, bar, maxArea, lineDrawer)
        )

    private def updateEditorPersistent(
        model: EditorModel,
        bar: BossBar,
        maxArea: Int,
        lineDrawer: LineDrawer,
    ): Unit =
        import EditorModelState.*

        sql.useFireAndForget(IO {
            updateBossBar(model.polygonArea, maxArea, bar)
            val couldWarLines = model.couldWarGroup match
                case None => List()
                case Some((_, _, polygon)) =>
                    val world = model.lines(0)._1.getWorld()
                    val points =
                        polygon.getCoordinates
                            .map(coord =>
                                Location(
                                    world,
                                    coord.getX,
                                    coord.getZ,
                                    coord.getY,
                                )
                            )
                            .toList
                            .dropRight(1)
                    val lines =
                        points
                            .sliding(2, 1)
                            .concat(List(List(points.last, points.head)))
                            .map(pair => (pair.head, pair(1)))
                            .toList
                    lines.map((p1, p2) => (p1, p2, LineColour.orange))
            val normalLines = model.lines.map { (p1, p2) =>
                (p1, p2, LineColour.white)
            }

            val pointLines =
                model.polygon.zipWithIndex.map { (point, idx) =>
                    val colour =
                        if model.state == lookingAt(point) then LineColour.teal
                        else if model.state == editingPoint(idx, false) then
                            LineColour.red
                        else if model.state == editingPoint(idx, true) then
                            LineColour.yellow
                        else LineColour.orange
                    (point, point.clone().add(0, 2, 0), colour)
                }

            val midpointLines =
                model.midpoints.map { (_, point) =>
                    val colour =
                        if model.state == lookingAt(point) then LineColour.teal
                        else LineColour.lime
                    (point, point.clone().add(0, 2, 0), colour)
                }

            val allLines = couldWarLines
                .concat(normalLines)
                .concat(pointLines)
                .concat(midpointLines)
            lineDrawer.setLines(allLines)
        }.evalOn(EntityExecutionContext(lineDrawer.player)))

    private def handleCreator(
        player: Player,
        model: CreatorModel,
        actions: List[CreatorAction],
        maxArea: Int,
        lineDrawer: LineDrawer,
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
                        val area =
                            try
                                val jtsPolygon = gf.createPolygon(
                                    points
                                        .appended(points.head)
                                        .map(point =>
                                            Coordinate(point.getX, point.getZ)
                                        )
                                        .toArray
                                )
                                jtsPolygon.getArea().toInt
                            catch
                                case e: IllegalArgumentException =>
                                    0
                        val emodel = EditorModel(model.beaconID, points)
                        val bar = createBossBar(area, maxArea, player)
                        updateEditorPersistent(emodel, bar, maxArea, lineDrawer)
                        Some(
                            PlayerState
                                .editing(emodel, bar, maxArea, lineDrawer)
                        )
                    case _ =>
                        None
            }
        done.lastOption match
            case None =>
                PlayerState.creating(model, actions, maxArea, lineDrawer)
            case Some(state) =>
                state

    def declare(player: Player): Unit =
        import cats.effect.unsafe.implicits.global
        IO {
            declareInner(player)
        }.unsafeRunAndForget()

    def declareInner(player: Player): Unit =
        val oldState = playerPolygons.get(player)
        val newState =
            oldState.flatMap { state =>
                state match
                    case PlayerState.editing(model, _, _, _) =>
                        model.couldWarGroup match
                            case None =>
                                Some(state)
                            case Some(_) =>
                                val jtsPolygon = gf.createPolygon(
                                    model.polygon
                                        .appended(model.polygon.head)
                                        .map(point =>
                                            Coordinate(
                                                point.getX,
                                                point.getZ,
                                                point.getY,
                                            )
                                        )
                                        .toArray
                                )
                                sql.useBlocking(
                                    sql.withS(
                                        sql.withTX(
                                            bm.updateBeaconPolygon(
                                                model.beaconID,
                                                model.polygon.head.getWorld,
                                                jtsPolygon,
                                            )
                                        )
                                    )
                                ) match
                                    case Left(
                                            err @ PolygonAdjustmentError
                                                .overlapsOneOtherPolygon(
                                                    defensiveBeacon,
                                                    defensiveGroup,
                                                    _,
                                                    Some(contestedArea),
                                                )
                                        ) =>
                                        sql.useBlocking(
                                            sql.withR(
                                                sql.withS(
                                                    sql.withTX(
                                                        battleManager.startBattle(
                                                            model.beaconID,
                                                            defensiveBeacon,
                                                            contestedArea,
                                                            jtsPolygon,
                                                            model.polygon.head.getWorld
                                                                .getUID(),
                                                        )
                                                    )
                                                )
                                            )
                                        ) match
                                            case Left(err) =>
                                                err match
                                                    case BattleError
                                                            .opponentIsInPrimeTime(
                                                                opensAt
                                                            ) =>
                                                        player
                                                            .sendServerMessage(
                                                                txt"The opponent's vulnerability window isn't open!"
                                                            )
                                                    case BattleError.beaconIsTooNew =>
                                                        player
                                                            .sendServerMessage(
                                                                txt"Your beacon needs to be at least 12 hours old to launch a battle!"
                                                            )
                                                Some(state)
                                            case Right(value) =>
                                                player.sendServerMessage(
                                                    txt"You have started declaring battle..."
                                                )
                                                None
                                    case Left(err) =>
                                        player.sendServerMessage(err.explain)
                                        Some(state)
                                    case Right(_) =>
                                        player.sendServerMessage(
                                            txt"A battle was unnecessary to take that land; your claims have been saved."
                                        )
                                        None
                    case _ =>
                        Some(state)
            }
        val _ = playerPolygons.updateWith(player) { _ => newState }

    def cancel(player: Player): Unit =
        clickPromises.remove(player) match
            case None =>
            case Some(value) =>
                value.failure(null)
        playerPolygons.remove(player) match
            case Some(PlayerState.editing(_, bar, _, lines)) =>
                val _ = bar.removeViewer(player)
                lines.clear()
            case _ =>

    def done(player: Player): Unit =
        val _ = playerPolygons.updateWith(player) { state =>
            state.flatMap(state =>
                state match
                    case PlayerState.editing(state, bar, maxArea, lineDrawer) =>
                        val (model, actions) = state.update(EditorMsg.done())
                        handleEditor(
                            player,
                            model,
                            actions,
                            bar,
                            maxArea,
                            lineDrawer,
                        )
                    case PlayerState.viewing(_, lineDrawer) =>
                        lineDrawer.clear()
                        None
                    case _ =>
                        Some(state)
            )
        }

    def leftClicked(player: Player, on: Location): Boolean =
        if playerPolygons.contains(player) then
            var viewing = false
            playerPolygons.updateWith(player) { state =>
                state.flatMap(state =>
                    state match
                        case PlayerState
                                .editing(state, bar, maxArea, lineDrawer) =>
                            val (model, actions) =
                                state.update(EditorMsg.leftClick())
                            handleEditor(
                                player,
                                model,
                                actions,
                                bar,
                                maxArea,
                                lineDrawer,
                            )
                        case PlayerState.viewing(_, _) =>
                            viewing = true
                            Some(state)
                        case _ =>
                            Some(state)
                )
            }
            !viewing
        else false

    def clicked(player: Player, on: Location): Boolean =
        if clickPromises.contains(player) then
            clickPromises.get(player).map(_.success(on))
            clickPromises.remove(player)
            true
        else if playerPolygons.contains(player) then
            var viewing = false
            val _ = playerPolygons.updateWith(player) { state =>
                state.flatMap(state =>
                    state match
                        case PlayerState
                                .editing(state, bar, maxArea, lineDrawer) =>
                            val (model, actions) =
                                state.update(EditorMsg.rightClick())
                            handleEditor(
                                player,
                                model,
                                actions,
                                bar,
                                maxArea,
                                lineDrawer,
                            )
                        case PlayerState
                                .creating(state, _, maxArea, lineDrawer) =>
                            val (model, actions) =
                                state.update(CreatorMsg.click(on))
                            Some(
                                handleCreator(
                                    player,
                                    model,
                                    actions,
                                    maxArea,
                                    lineDrawer,
                                )
                            )
                        case PlayerState.viewing(_, _) =>
                            viewing = true
                            Some(state)
                )
            }
            !viewing
        else false

    def look(player: Player, targetLoc: Location): Unit =
        if !playerPolygons.contains(player) then return

        val _ = playerPolygons.updateWith(player) { state =>
            state.flatMap(state =>
                state match
                    case PlayerState.editing(state, bar, maxArea, lineDrawer) =>
                        val (model, actions) =
                            state.update(EditorMsg.look(targetLoc))
                        handleEditor(
                            player,
                            model,
                            actions,
                            bar,
                            maxArea,
                            lineDrawer,
                        )
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

    // def drawLine(
    //     fromBlock: Location,
    //     toBlock: Location,
    //     showingTo: Player,
    //     color: Color,
    //     detail: Double,
    // ): Unit =
    //     val start = fromBlock.clone().add(0.5, 1.0, 0.5)
    //     val finish = toBlock.clone().add(0.5, 1.0, 0.5)

    //     val dir = finish.toVector.subtract(start.toVector).normalize()
    //     val len = start.distance(finish)

    //     for i <- Iterator.iterate(0.0)(_ + detail).takeWhile(_ < len) do
    //         val offset = dir.clone().multiply(i)
    //         val pos = start.clone().add(offset)

    //         pos.getWorld
    //             .spawnParticle(
    //                 Particle.REDSTONE,
    //                 ju.Arrays.asList(showingTo),
    //                 showingTo,
    //                 pos.getX,
    //                 pos.getY,
    //                 pos.getZ,
    //                 4,
    //                 0.0,
    //                 0.0,
    //                 0.0,
    //                 0.0,
    //                 DustOptions(color, 1.0),
    //             )
