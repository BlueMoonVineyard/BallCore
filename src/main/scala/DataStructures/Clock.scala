// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.DataStructures

import java.time.temporal.TemporalAmount
import java.time.OffsetDateTime

trait Clock:
    def now(): OffsetDateTime

class WallClock extends Clock:
    override def now(): OffsetDateTime =
        OffsetDateTime.now()

class TestClock(val start: OffsetDateTime) extends Clock:
    var time = start
    override def now(): OffsetDateTime =
        time
    def reset() =
        time = start
    def changeTimeBy(t: TemporalAmount) =
        time = time.plus(t)
