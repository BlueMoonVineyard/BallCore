// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import CivCubed.Rest.RestManagerHooks
import cats.effect.IO
import java.{util => ju}
import CivCubed.Storage.SQLManager
import CivCubed.Rest.RestManager
import CivCubed.DataStructures.TestClock
import java.time.OffsetDateTime
import java.util.UUID
import CivCubed.Rest.Bounds
import org.bukkit.Location
import be.seeseemelk.mockbukkit.WorldMock
import CivCubed.Rest.Evaluation
import CivCubed.Rest.RoomRole
import java.time.Duration
import skunk.Session

class TestRestHooks() extends RestManagerHooks:
    val queue = scala.collection.mutable.Queue[Option[(RoomRole, Evaluation, Bounds)]]()
    override def updateSidebar(playerID: ju.UUID, rest: Double): IO[Unit] =
        IO.pure(())
    override def evaluateRoomAt(rm: RestManager, location: Location)(using Session[IO]): IO[Option[(RoomRole, Evaluation, Bounds)]] =
        IO { queue.dequeue }

class RestSuite extends munit.FunSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    // sql.test("rest can be used up within 4000 actions") { implicit sql =>
    //     given hooks: RestManagerHooks = TestRestHooks()
    //     given clock: TestClock = TestClock(OffsetDateTime.now())
    //     given rest: RestManager = RestManager()

    //     val player = UUID.randomUUID()
    //     sql.useBlocking(
    //         sql.withS(
    //             (1 to 4000).toList.traverse { _ => rest.useRest(player) }
    //         )
    //     )

    //     assert(
    //         !sql.useBlocking(sql.withS(rest.useRest(player))),
    //         "rest should be used up after 4000 uses",
    //     )
    // }
    // sql.test("rest is restored after some time") { implicit sql =>
    //     given hooks: RestManagerHooks = TestRestHooks()
    //     given clock: TestClock = TestClock(OffsetDateTime.now())
    //     given rest: RestManager = RestManager()

    //     val player = UUID.randomUUID()
    //     sql.useBlocking(
    //         sql.withS(
    //             (1 to 4000).toList.traverse { _ => rest.useRest(player) }
    //         )
    //     )

    //     assert(
    //         !sql.useBlocking(sql.withS(rest.useRest(player))),
    //         "rest should be used up after 4000 uses",
    //     )

    //     sql.useBlocking(sql.withS(rest.logoff(player)))

    //     clock.changeTimeBy(Duration.ofHours(12))

    //     sql.useBlocking(sql.withS(rest.logon(player)))

    //     assert(
    //         sql.useBlocking(sql.withS(rest.useRest(player))),
    //         "rest should be rejuvinated after being logged off",
    //     )
    // }
    sql.test("basic role switching works") { implicit sql =>
        given hooks: TestRestHooks = TestRestHooks()
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given rest: RestManager = RestManager()

        sql.refresh()

        val mock = WorldMock()
        val player = UUID.randomUUID()

        val location = Location(mock, 0, 0, 0)

        sql.useBlocking(sql.withS { rest.logon(player, location) })
        sql.useBlocking(sql.withS(rest.setSpawnLocation(player, (0, 0, 0), mock.getUID)))
        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logoff(player, location)))

        clock.changeTimeBy(Duration.ofHours(12))

        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logon(player, location)))
        assertEquals(
            sql.useBlocking(sql.withR(rest.getSpecialization(player))),
            (RoomRole.mining, Evaluation.nothing),
            "rest should be modest after first sleep"
        )
        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logoff(player, location)))

        clock.changeTimeBy(Duration.ofHours(12))

        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logon(player, location)))
        assertEquals(
            sql.useBlocking(sql.withR(rest.getSpecialization(player))),
            (RoomRole.mining, Evaluation.modest),
            "rest should be modest after second sleep"
        )
        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logoff(player, location)))

        clock.changeTimeBy(Duration.ofHours(12))

        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logon(player, location)))
        assertEquals(
            sql.useBlocking(sql.withR(rest.getSpecialization(player))),
            (RoomRole.mining, Evaluation.good),
            "rest should be good after third sleep"
        )
        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logoff(player, location)))

        clock.changeTimeBy(Duration.ofHours(12))

        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.good, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logon(player, location)))
        assertEquals(
            sql.useBlocking(sql.withR(rest.getSpecialization(player))),
            (RoomRole.mining, Evaluation.good),
            "rest should still be good after fourth sleep"
        )
        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.luxurious, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logoff(player, location)))

        clock.changeTimeBy(Duration.ofHours(12))

        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.luxurious, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logon(player, location)))
        assertEquals(
            sql.useBlocking(sql.withR(rest.getSpecialization(player))),
            (RoomRole.mining, Evaluation.luxurious),
            "rest should be luxurious after fifth sleep after improving the room"
        )
        hooks.queue.enqueue(Some((RoomRole.mining, Evaluation.luxurious, Bounds(1, 1, 1, -1, -1, -1))))
        sql.useBlocking(sql.withS(rest.logoff(player, location)))
    }
    sql.test("spawn locations work") { implicit sql =>
        given hooks: TestRestHooks = TestRestHooks()
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given rest: RestManager = RestManager()

        sql.refresh()

        val player = UUID.randomUUID()
        val mock = WorldMock()
        val location = Location(mock, 0, 0, 0)
        val world = mock.getUID

        hooks.queue.enqueue(None)
        sql.useBlocking(sql.withS(rest.logon(player, location)))

        sql.useBlocking(sql.withS(rest.setSpawnLocation(player, (0, 0, 0), world)))

        val items1 = sql.useBlocking(sql.withS(rest.getSpawnLocationsWithin((-1, -1, -1), (1, 1, 1), world)))
        assert(items1.contains(player), "player should be there")

        sql.useBlocking(sql.withS(rest.setSpawnLocation(player, (0, 5, 0), world)))

        val items2 = sql.useBlocking(sql.withS(rest.getSpawnLocationsWithin((-1, -1, -1), (1, 1, 1), world)))
        assert(!items2.contains(player), "player should not be there")
    }
}
