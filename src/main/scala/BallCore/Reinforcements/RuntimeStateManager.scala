// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import java.util as ju
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.Map

object RuntimeStateManager:
    val states: mutable.Map[_root_.java.util.UUID, _root_.java.util.UUID] =
        mutable.Map[ju.UUID, ju.UUID]()
