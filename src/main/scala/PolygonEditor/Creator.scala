// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.PolygonEditor

import org.bukkit.Location
import net.kyori.adventure.text.Component
import BallCore.Beacons.BeaconID

enum CreatorModelState:
	case definingPointA()
	case definingPointB(pointA: Location)
	case definingPointC(pointA: Location, pointB: Location)
	case definingPointD(pointA: Location, pointB: Location, pointC: Location)

enum CreatorAction:
	case drawLine(from: Location, to: Location)
	case drawSelectionLine(from: Location)
	case drawFinisherLine(to: Location)

	case notifyPlayer(message: Component)

	case finished(points: List[Location])

enum CreatorMsg:
	case click(at: Location)

case class CreatorModel(
	beaconID: BeaconID,
	state: CreatorModelState
) extends Model[CreatorModel, CreatorMsg, CreatorAction]:
	import BallCore.UI.ChatElements._

	def update(msg: CreatorMsg): (CreatorModel, List[CreatorAction]) =
		import CreatorMsg._
		import CreatorModelState._
		import CreatorAction._

		(state, msg) match
			case (definingPointA(), click(pointA)) =>
				this.copy(state = definingPointB(pointA))
				-> List(drawSelectionLine(pointA), notifyPlayer(txt"Press ${keybind("key.use")} to place Point 2"))
			case (definingPointB(pointA), click(pointB)) =>
				this.copy(state = definingPointC(pointA, pointB))
				-> List(drawLine(pointA, pointB), drawSelectionLine(pointB), notifyPlayer(txt"Press ${keybind("key.use")} to place Point 3"))
			case (definingPointC(pointA, pointB), click(pointC)) =>
				this.copy(state = definingPointD(pointA, pointB, pointC))
				-> List(drawLine(pointA, pointB), drawLine(pointB, pointC), drawSelectionLine(pointC), drawFinisherLine(pointA), notifyPlayer(txt"Press ${keybind("key.use")} to place Point 4"))
			case (definingPointD(pointA, pointB, pointC), click(pointD)) =>
				this -> List(finished(List(pointA, pointB, pointC, pointD)))
