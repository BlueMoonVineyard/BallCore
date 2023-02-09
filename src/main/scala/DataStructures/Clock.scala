// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.DataStructures

import java.time.Instant
import java.time.temporal.TemporalAmount

trait Clock:
    def now(): Instant

class WallClock extends Clock:
    override def now(): Instant =
        Instant.now()

class TestClock(val start: Instant) extends Clock:
    var time = start
    override def now(): Instant =
        time
    def changeTimeBy(t: TemporalAmount) =
        time = time.plus(t)
