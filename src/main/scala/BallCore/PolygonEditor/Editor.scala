// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.PolygonEditor

import BallCore.Beacons.BeaconID
import net.kyori.adventure.text.Component
import org.bukkit.{Location, World}
import org.locationtech.jts.geom.{Coordinate, GeometryFactory, Polygon}
import BallCore.Groups.GroupID

val gf = GeometryFactory()

enum EditorModelState:
    case idle()
    case lookingAt(loc: Location)
    case editingPoint(idx: Int, valid: Boolean)

enum EditorMsg:
    case rightClick()
    case leftClick()
    case look(at: Location)
    case done()

enum EditorAction:
    case notifyPlayer(msg: Component)
    case finished()

object EditorModel:
    def apply(beaconID: BeaconID, polygon: List[Location]): EditorModel =
        EditorModel(beaconID, polygon, EditorModelState.idle(), None)

    def apply(beaconID: BeaconID, polygon: Polygon, world: World): EditorModel =
        EditorModel(
            beaconID,
            polygon.getCoordinates
                .map(coord =>
                    Location(world, coord.getX, coord.getZ, coord.getY)
                )
                .toList
                .dropRight(1),
        )

case class EditorModel(
    beaconID: BeaconID,
    polygon: List[Location],
    state: EditorModelState,
    couldWarGroup: Option[(GroupID, BeaconID, Polygon)],
) extends Model[EditorModel, EditorMsg, EditorAction]:

    import BallCore.UI.ChatElements.*
    import EditorAction.*
    import EditorModelState.*
    import EditorMsg.*

    private def midpoint(p1: Location, p2: Location): Location =
        p1.clone().add(p2.clone()).multiply(0.5)

    private def validate(polygon: List[Location]): Boolean =
        if polygon.length < 4 then return false

        try
            val jtsPolygon = gf.createPolygon(
                polygon
                    .appended(polygon.head)
                    .map(point => Coordinate(point.getX, point.getZ))
                    .toArray
            )
            jtsPolygon.isSimple && jtsPolygon.isValid
        catch
            case e: IllegalArgumentException =>
                false

    lazy val polygonArea: Int =
        try
            val jtsPolygon = gf.createPolygon(
                polygon
                    .appended(polygon.head)
                    .map(point => Coordinate(point.getX, point.getZ))
                    .toArray
            )
            jtsPolygon.getArea().toInt
        catch
            case e: IllegalArgumentException =>
                0
    lazy val lines: List[(Location, Location)] =
        polygon
            .sliding(2, 1)
            .concat(List(List(polygon.last, polygon.head)))
            .map(pair => (pair.head, pair(1)))
            .toList
    lazy val midpoints: List[(Location, Location)] =
        lines.map((p1, p2) => (p1, midpoint(p1, p2)))

    def update(msg: EditorMsg): (EditorModel, List[EditorAction]) =
        msg match
            case EditorMsg.rightClick() =>
                updateRightClick()
            case EditorMsg.leftClick() =>
                updateLeftClick()
            case EditorMsg.look(loc) =>
                updateLookingAt(loc)
            case EditorMsg.done() =>
                (this, List(EditorAction.finished()))

    private def updateLeftClick(): (EditorModel, List[EditorAction]) =
        state match
            case idle() =>
                this -> List()
            case editingPoint(_, _) =>
                this -> List()
            case lookingAt(loc) =>
                val nieuw = polygon.filterNot(_ == loc)
                if validate(nieuw) then this.copy(polygon = nieuw) -> List()
                else
                    this -> List(
                        notifyPlayer(
                            txt"Removing this point would make the polygon invalid"
                        )
                    )

    private def updateRightClick(): (EditorModel, List[EditorAction]) =
        state match
            case idle() =>
                this -> List()
            case editingPoint(_, _) =>
                this.copy(state = idle()) -> List()
            case lookingAt(loc) =>
                midpoints.find(_._2 == loc) match
                    case None =>
                        this.copy(state =
                            editingPoint(polygon.indexOf(loc), true)
                        ) -> List()
                    case Some((prev, point)) =>
                        val (front, back) =
                            polygon.splitAt(polygon.indexOf(prev) + 1)
                        val nieuw = front ++ (point :: back)
                        this.copy(
                            polygon = nieuw,
                            editingPoint(nieuw.indexOf(point), true),
                        ) -> List()

    private def updateLookingAt(
        targetLoc: Location
    ): (EditorModel, List[EditorAction]) =
        state match
            case editingPoint(idx, _) =>
                val nieuw = polygon.updated(idx, targetLoc)
                if validate(nieuw) then
                    this.copy(
                        polygon = nieuw,
                        state = editingPoint(idx, true),
                    ) -> List()
                else
                    this.copy(
                        state = editingPoint(idx, false),
                        couldWarGroup = None,
                    ) -> List()
            case idle() | lookingAt(_) =>
                val newState =
                    polygon
                        .concat(midpoints.map(_._2))
                        .map(p => (p, p.distance(targetLoc)))
                        .filter(_._2 < 2)
                        .sortBy(_._2)
                        .headOption
                        .map(_._1) match
                        case None => idle()
                        case Some(loc) => lookingAt(loc)

                this.copy(state = newState) -> List()
