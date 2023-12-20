// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.TestClock
import java.time.OffsetDateTime
import BallCore.Reinforcements.BustThroughTracker
import be.seeseemelk.mockbukkit.WorldMock
import org.bukkit.Location
import BallCore.Reinforcements.BustResult
import java.time.Duration

class BustThroughSuite extends munit.FunSuite {
    test("bust throughs work") {
        given tc: TestClock = TestClock(OffsetDateTime.now())

        val world = WorldMock()
        val btt = BustThroughTracker()

        for i <- 1 to btt.blockHealth - 1 do
            tc.changeTimeBy(Duration.ofSeconds(30))
            assertEquals(btt.bust(Location(world, 0, 0, 0), 0), BustResult.busting)

        assertEquals(btt.bust(Location(world, 0, 0, 0), 0), BustResult.justBusted)
        assertEquals(
            btt.bust(Location(world, 0, 0, 0), 0),
            BustResult.alreadyBusted,
        )
    }
    test("bust throughs expire") {
        given tc: TestClock = TestClock(OffsetDateTime.now())

        val world = WorldMock()
        val btt = BustThroughTracker()

        for i <- 1 to btt.blockHealth - 1 do
            tc.changeTimeBy(Duration.ofSeconds(30))
            assertEquals(btt.bust(Location(world, 0, 0, 0), 0), BustResult.busting)

        assertEquals(btt.bust(Location(world, 0, 0, 0), 0), BustResult.justBusted)
        assertEquals(
            btt.bust(Location(world, 0, 0, 0), 0),
            BustResult.alreadyBusted,
        )

        tc.changeTimeBy(Duration.ofMinutes(btt.expiryMinutes + 1))
        assertEquals(btt.bust(Location(world, 0, 0, 0), 0), BustResult.busting)
    }
    test("partial bust throughs expire") {
        given tc: TestClock = TestClock(OffsetDateTime.now())

        val world = WorldMock()
        val btt = BustThroughTracker()

        for i <- 1 to btt.blockHealth - 1 do
            tc.changeTimeBy(Duration.ofSeconds(30))
            assertEquals(btt.bust(Location(world, 0, 0, 0), 0), BustResult.busting)

        tc.changeTimeBy(Duration.ofMinutes(btt.expiryMinutes + 1))
        assertEquals(btt.bust(Location(world, 0, 0, 0), 0), BustResult.busting)
    }
}
