// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.TestClock
import BallCore.Datekeeping.{Datekeeping, GameDate}

import java.time.Duration

class DatekeepingSuite extends munit.FunSuite:
    test("basic datekeeping") {
        given uhr: TestClock = TestClock(Datekeeping.epoch)

        val jetzt = Datekeeping.time()
        assertEquals(jetzt, GameDate(Datekeeping.year0, 0, 1, 0, 0))

        uhr.changeTimeBy(Duration.ofMinutes(60))
        val oneDayFromJetzt = Datekeeping.time()
        assertEquals(oneDayFromJetzt, GameDate(Datekeeping.year0, 0, 2, 0, 0))
    }
