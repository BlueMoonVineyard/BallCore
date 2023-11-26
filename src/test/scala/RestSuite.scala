// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Rest.RestManagerHooks
import cats.effect.IO
import java.{util => ju}
import BallCore.Storage.SQLManager
import BallCore.Rest.RestManager
import BallCore.DataStructures.TestClock
import java.time.OffsetDateTime
import java.util.UUID
import cats.syntax.all._
import java.time.Duration

class TestRestHooks extends RestManagerHooks:
    override def updateSidebar(playerID: ju.UUID, rest: Double): IO[Unit] =
        IO.pure(())

class RestSuite extends munit.FunSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    sql.test("rest can be used up within 2000 actions") { implicit sql =>
        given hooks: RestManagerHooks = TestRestHooks()
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given rest: RestManager = RestManager()

        val player = UUID.randomUUID()
        sql.useBlocking(
            (1 to 2000).toList.traverse { _ => rest.useRest(player) }
        )

        assert(!sql.useBlocking(rest.useRest(player)), "rest should be used up after 2000 uses")
    }
    sql.test("rest is restored after some time") { implicit sql =>
        given hooks: RestManagerHooks = TestRestHooks()
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given rest: RestManager = RestManager()

        val player = UUID.randomUUID()
        sql.useBlocking(
            (1 to 2000).toList.traverse { _ => rest.useRest(player) }
        )

        assert(!sql.useBlocking(rest.useRest(player)), "rest should be used up after 2000 uses")

        sql.useBlocking(rest.logoff(player))

        clock.changeTimeBy(Duration.ofHours(12))

        sql.useBlocking(rest.logon(player))

        assert(sql.useBlocking(rest.useRest(player)), "rest should be rejuvinated after being logged off")
    }
}
