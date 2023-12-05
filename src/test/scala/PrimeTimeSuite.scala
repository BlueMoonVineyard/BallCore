// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.TestClock
import BallCore.Storage.SQLManager
import BallCore.Groups

import java.time.OffsetDateTime
import BallCore.PrimeTime.PrimeTimeManager
import java.util.UUID
import java.time.OffsetTime
import java.time.Duration

class PrimeTimeSuite extends munit.FunSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    sql.test("prime time basic case works") { implicit sql =>
        given gm: Groups.GroupManager = new Groups.GroupManager
        given clock: TestClock =
            new TestClock(OffsetDateTime.parse("2023-12-01T06:00:00+00:00"))
        given pm: PrimeTimeManager = new PrimeTimeManager()

        val ownerID = UUID.randomUUID()

        val groupID = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(ownerID, "woot")))
        )

        val result = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    pm.setGroupPrimeTime(
                        ownerID,
                        groupID,
                        OffsetTime.parse("06:00:00+00:00"),
                    )
                )
            )
        )
        assert(result.isRight, "should've been able to set prime time")

        clock.changeTimeBy(Duration.ofHours(1))
        assert(sql.useBlocking(
            sql.withS(
                sql.withTX(
                    pm.isGroupInPrimeTime(
                        groupID,
                    )
                )
            )
        ), "we should be in prime time after an hour from the start of the window")

        clock.changeTimeBy(Duration.ofHours(7))
        assert(!sql.useBlocking(
            sql.withS(
                sql.withTX(
                    pm.isGroupInPrimeTime(
                        groupID,
                    )
                )
            )
        ), "we shouldnt be in prime time 8 hours from the start of the window (it ended 2 hours ago)")
    }
    sql.test("prime time graveyard shift works") { implicit sql =>
        given gm: Groups.GroupManager = new Groups.GroupManager
        given clock: TestClock =
            new TestClock(OffsetDateTime.parse("2023-12-01T22:00:00+00:00"))
        given pm: PrimeTimeManager = new PrimeTimeManager()

        val ownerID = UUID.randomUUID()

        val groupID = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(ownerID, "woot")))
        )

        val result = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    pm.setGroupPrimeTime(
                        ownerID,
                        groupID,
                        OffsetTime.parse("22:00:00+00:00"),
                    )
                )
            )
        )
        assert(result.isRight, "should've been able to set prime time")

        clock.changeTimeBy(Duration.ofHours(1))
        assert(sql.useBlocking(
            sql.withS(
                sql.withTX(
                    pm.isGroupInPrimeTime(
                        groupID,
                    )
                )
            )
        ), "we should be in prime time before the rollover")

        clock.changeTimeBy(Duration.ofHours(2))
        assert(sql.useBlocking(
            sql.withS(
                sql.withTX(
                    pm.isGroupInPrimeTime(
                        groupID,
                    )
                )
            )
        ), "we should be in prime time after the rollover")

        clock.changeTimeBy(Duration.ofHours(5))
        assert(!sql.useBlocking(
            sql.withS(
                sql.withTX(
                    pm.isGroupInPrimeTime(
                        groupID,
                    )
                )
            )
        ), "we shouldnt be in prime time after the shift ending")
    }
}
