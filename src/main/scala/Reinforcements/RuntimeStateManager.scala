// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import java.{util => ju}
import scala.collection.mutable.Map

sealed trait PlayerState
case class Neutral() extends PlayerState
case class Reinforcing() extends PlayerState
case class Unreinforcing() extends PlayerState
case class ReinforceAsYouGo() extends PlayerState

object RuntimeStateManager:
    val states = Map[ju.UUID, PlayerState]().withDefault(x => Neutral())
