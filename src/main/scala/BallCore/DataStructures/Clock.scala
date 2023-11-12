// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.DataStructures

import java.time.OffsetDateTime
import java.time.temporal.TemporalAmount

trait Clock:
  def now(): OffsetDateTime

class WallClock extends Clock:
  override def now(): OffsetDateTime =
    OffsetDateTime.now()

class TestClock(val start: OffsetDateTime) extends Clock:
  var time: OffsetDateTime = start

  override def now(): OffsetDateTime =
    time

  def reset(): Unit =
    time = start

  def changeTimeBy(t: TemporalAmount): Unit =
    time = time.plus(t)
