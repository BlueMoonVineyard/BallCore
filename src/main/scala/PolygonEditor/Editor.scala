package BallCore.PolygonEditor

import net.kyori.adventure.text.Component
import org.locationtech.jts.geom.GeometryFactory
import org.bukkit.Location
import org.locationtech.jts.geom.Coordinate
import BallCore.Beacons.BeaconID
import org.locationtech.jts.geom.Polygon
import org.bukkit.World

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
		EditorModel(beaconID, polygon, EditorModelState.idle())
	def apply(beaconID: BeaconID, polygon: Polygon, world: World): EditorModel =
		EditorModel(beaconID, polygon.getCoordinates().map( coord => Location(world, coord.getX(), coord.getZ(), coord.getY()) ).toList.dropRight(1))

case class EditorModel(
	beaconID: BeaconID,
	polygon: List[Location],

	state: EditorModelState,
) extends Model[EditorModel, EditorMsg, EditorAction]:
	import EditorMsg._
	import EditorModelState._
	import EditorAction._
	import BallCore.UI.ChatElements._

	def midpoint(p1: Location, p2: Location): Location =
		p1.clone().add(p2.clone()).multiply(0.5)

	def validate(polygon: List[Location]): Boolean =
		if polygon.length < 4 then
			return false

		try
			val jtsPolygon = gf.createPolygon(
				polygon.appended(polygon.head)
					.map(point => Coordinate(point.getX(), point.getZ()))
					.toArray
			)
			jtsPolygon.isSimple() && jtsPolygon.isValid()
		catch
			case e: IllegalArgumentException =>
				false

	lazy val lines =
		polygon.sliding(2, 1)
			.concat(List(List(polygon.last, polygon(0))))
			.map(pair => (pair(0), pair(1)))
			.toList
	lazy val midpoints =
		lines.map((p1, p2) => (p1, midpoint(p1, p2))).toList

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

	def updateLeftClick(): (EditorModel, List[EditorAction]) =
		state match
			case idle() =>
				this -> List()
			case editingPoint(_, _) =>
				this -> List()
			case lookingAt(loc) =>
				val nieuw = polygon.filterNot(_ == loc)
				if validate(nieuw) then
					this.copy(polygon = nieuw) -> List()
				else
					this -> List(notifyPlayer(txt"Removing this point would make the polygon invalid"))

	def updateRightClick(): (EditorModel, List[EditorAction]) =
		state match
			case idle() =>
				this -> List()
			case editingPoint(_, _) =>
				this.copy(state = idle()) -> List()
			case lookingAt(loc) =>
				midpoints.find(_._2 == loc) match
					case None =>
						this.copy(state = editingPoint(polygon.indexOf(loc), true)) -> List()
					case Some((prev, point)) =>
						val (front, back) = polygon.splitAt(polygon.indexOf(prev) + 1)
						val nieuw = front ++ (point :: back)
						this.copy(polygon = nieuw, editingPoint(nieuw.indexOf(point), true)) -> List()

	def updateLookingAt(targetLoc: Location): (EditorModel, List[EditorAction]) =
		state match
			case editingPoint(idx, _) =>
				val nieuw = polygon.updated(idx, targetLoc)
				if validate(nieuw) then
					this.copy(polygon = nieuw, state = editingPoint(idx, true)) -> List()
				else
					this.copy(state = editingPoint(idx, false)) -> List()
			case idle() | lookingAt(_) =>
				val newState =
					polygon.concat(midpoints.map(_._2))
						.map(p => (p, p.distance(targetLoc)))
						.filter(_._2 < 2)
						.sortBy(_._2)
						.headOption.map(_._1)
					match
						case None => idle()
						case Some(loc) => lookingAt(loc)

				this.copy(state = newState) -> List()
