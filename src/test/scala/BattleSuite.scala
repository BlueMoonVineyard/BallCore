// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage.SQLManager
import BallCore.Groups.GroupManager
import BallCore.Beacons.CivBeaconManager
import org.bukkit.Location
import be.seeseemelk.mockbukkit.WorldMock
import java.util.UUID
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import BallCore.Sigils.BattleManager

import BallCore.Sigils.BattleHooks
import BallCore.Sigils.BattleID
import cats.effect.IO
import munit.Assertions
import scala.collection.mutable
import BallCore.Beacons.BeaconID
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Geometry
import skunk.Session
import BallCore.Beacons.BeaconManagerHooks
import skunk.Transaction
import cats.effect.kernel.Deferred
import BallCore.Beacons.IngameBeaconManagerHooks
import BallCore.PrimeTime.PrimeTimeManager
import BallCore.DataStructures.TestClock
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Duration
import BallCore.Sigils.BattleError

object DummyBeaconHooks:
    def it: Deferred[IO, BeaconManagerHooks] =
        new Deferred:
            override def get: IO[BeaconManagerHooks] =
                IO.pure(DummyBeaconHooks())
            override def tryGet: IO[Option[BeaconManagerHooks]] =
                IO.pure(Some(DummyBeaconHooks()))
            override def complete(it: BeaconManagerHooks): IO[Boolean] =
                IO.pure(false)

class DummyBeaconHooks extends BeaconManagerHooks:
    override def beaconDeletionIncoming(
        beaconID: BeaconID
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        IO.pure(())

class TestBattleHooks(using assertions: Assertions) extends BattleHooks:
    val spawnQueue: mutable.Queue[Unit] =
        mutable.Queue[Unit]()
    val defendQueue: mutable.Queue[Unit] =
        mutable.Queue[Unit]()
    val takeQueue: mutable.Queue[Unit] =
        mutable.Queue[Unit]()

    def expectSpawn() = spawnQueue.enqueue(())
    override def spawnPillarFor(
        battle: BattleID,
        offense: BeaconID,
        contestedArea: Geometry,
        world: UUID,
        defense: BeaconID,
    )(using Session[IO]): IO[Unit] =
        IO {
            assertions.assert(!spawnQueue.isEmpty, "unexpected spawn pillar")
            spawnQueue.dequeue()
        }
    def expectDefended() = defendQueue.enqueue(())
    override def battleDefended(
        battle: BattleID,
        offense: BeaconID,
        defense: BeaconID,
    )(using Session[IO]): IO[Unit] =
        IO {
            assertions.assert(!defendQueue.isEmpty, "unexpected defended")
            defendQueue.dequeue()
        }
    def expectTaken() = takeQueue.enqueue(())
    override def battleTaken(
        battle: BattleID,
        offense: BeaconID,
        defense: BeaconID,
        contestedArea: Polygon,
        desiredArea: Polygon,
        world: UUID,
    )(using Session[IO]): IO[Unit] =
        IO {
            assertions.assert(!takeQueue.isEmpty, "unexpected taken")
            takeQueue.dequeue()
        }

class BattleSuite extends munit.FunSuite:
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)

    sql.test("battle manager pillar amount") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given it: Deferred[IO, BeaconManagerHooks] = DummyBeaconHooks.it
        given hn: CivBeaconManager = CivBeaconManager()
        given hooks: TestBattleHooks = TestBattleHooks(using this)
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given PrimeTimeManager = PrimeTimeManager()
        given battleManager: BattleManager = BattleManager()
        val world = WorldMock()

        val sizes = List((10, 1), (12, 2))
        sizes.foreach { (size, pillarCount) =>
            val offset = 2 * size

            val offensiveOwner = UUID.randomUUID()
            val offensiveLocation = Location(world, 0, -10, 0)
            val (offensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(hn.placeHeart(offensiveLocation, offensiveOwner))
                ).toOption
                    .get

            val defensiveOwner = UUID.randomUUID()
            val defensiveLocation = Location(world, offset, 10, offset)
            val (defensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(
                        hn.placeHeart(
                            defensiveLocation,
                            defensiveOwner,
                        )
                    )
                ).toOption
                    .get

            clock.changeTimeBy(Duration.ofDays(4))

            assertNotEquals(
                offensiveBeacon,
                defensiveBeacon,
                "different heart IDs",
            )

            val gf = GeometryFactory()
            val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
            assert(
                area1.covers(gf.createPoint(Coordinate(0, 0, -10))),
                "sanity check of area 1",
            )
            val res1 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(offensiveBeacon, world, area1)
                    )
                )
            )
            assert(res1.isRight, (res1, "setting first heart's area"))

            val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
            assert(
                area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
                "sanity check of area 2",
            )
            val res2 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(defensiveBeacon, world, area2)
                    )
                )
            )
            assert(res2.isRight, (res2, "setting second heart's area"))

            for _ <- 1 to pillarCount do hooks.expectSpawn()
            val battle = sql
                .useBlocking(
                    sql.withS(
                        sql.withTX(
                            battleManager.startBattle(
                                offensiveBeacon,
                                defensiveBeacon,
                                area2,
                                area2,
                                world.getUID(),
                            )
                        )
                    )
                )
                .toOption
                .get

            hooks.expectDefended()
            sql.useBlocking(sql.withS(battleManager.offensiveResign(battle)))

            sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.removeHeart(defensiveLocation, defensiveOwner)
                    )
                )
            )
            sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.removeHeart(offensiveLocation, offensiveOwner)
                    )
                )
            )
        }
    }
    sql.test("battle manager defense") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given it: Deferred[IO, BeaconManagerHooks] = DummyBeaconHooks.it
        given hn: CivBeaconManager = CivBeaconManager()
        given hooks: TestBattleHooks = TestBattleHooks(using this)
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given PrimeTimeManager = PrimeTimeManager()
        given battleManager: BattleManager = BattleManager()
        val world = WorldMock()

        val offset = 2 * 10

        val offensiveOwner = UUID.randomUUID()
        val offensiveLocation = Location(world, 0, -10, 0)
        val (offensiveBeacon, _) =
            sql.useBlocking(
                sql.withS(hn.placeHeart(offensiveLocation, offensiveOwner))
            ).toOption
                .get

        val defensiveOwner = UUID.randomUUID()
        val defensiveLocation = Location(world, offset, 10, offset)
        val (defensiveBeacon, _) =
            sql.useBlocking(
                sql.withS(
                    hn.placeHeart(
                        defensiveLocation,
                        defensiveOwner,
                    )
                )
            ).toOption
                .get

        clock.changeTimeBy(Duration.ofDays(4))

        assertNotEquals(
            offensiveBeacon,
            defensiveBeacon,
            "different heart IDs",
        )

        val gf = GeometryFactory()
        val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
        assert(
            area1.covers(gf.createPoint(Coordinate(0, 0, -10))),
            "sanity check of area 1",
        )
        val res1 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    hn.updateBeaconPolygon(offensiveBeacon, world, area1)
                )
            )
        )
        assert(res1.isRight, (res1, "setting first heart's area"))

        val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
        assert(
            area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
            "sanity check of area 2",
        )
        val res2 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    hn.updateBeaconPolygon(defensiveBeacon, world, area2)
                )
            )
        )
        assert(res2.isRight, (res2, "setting second heart's area"))

        hooks.expectSpawn()
        val battle = sql
            .useBlocking(
                sql.withS(
                    sql.withTX(
                        battleManager.startBattle(
                            offensiveBeacon,
                            defensiveBeacon,
                            area2,
                            area2,
                            world.getUID(),
                        )
                    )
                )
            )
            .toOption
            .get

        for _ <- battleManager.initialHealth + 1 to 10 do
            hooks.expectSpawn()
            sql.useBlocking(sql.withS(battleManager.pillarDefended(battle)))

        hooks.expectDefended()
        sql.useBlocking(sql.withS(battleManager.pillarDefended(battle)))

        sql.useBlocking(
            sql.withS(
                sql.withTX(hn.removeHeart(defensiveLocation, defensiveOwner))
            )
        )
        sql.useBlocking(
            sql.withS(
                sql.withTX(hn.removeHeart(offensiveLocation, offensiveOwner))
            )
        )
    }
    sql.test("battle manager conquest") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given it: Deferred[IO, BeaconManagerHooks] = DummyBeaconHooks.it
        given hn: CivBeaconManager = CivBeaconManager()
        given hooks: TestBattleHooks = TestBattleHooks(using this)
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given PrimeTimeManager = PrimeTimeManager()
        given battleManager: BattleManager = BattleManager()
        val world = WorldMock()

        val offset = 2 * 10

        val offensiveOwner = UUID.randomUUID()
        val offensiveLocation = Location(world, 0, -10, 0)
        val (offensiveBeacon, _) =
            sql.useBlocking(
                sql.withS(hn.placeHeart(offensiveLocation, offensiveOwner))
            ).toOption
                .get

        val defensiveOwner = UUID.randomUUID()
        val defensiveLocation = Location(world, offset, 10, offset)
        val (defensiveBeacon, _) =
            sql.useBlocking(
                sql.withS(
                    hn.placeHeart(
                        defensiveLocation,
                        defensiveOwner,
                    )
                )
            ).toOption
                .get

        clock.changeTimeBy(Duration.ofDays(4))

        assertNotEquals(
            offensiveBeacon,
            defensiveBeacon,
            "different heart IDs",
        )

        val gf = GeometryFactory()
        val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
        assert(
            area1.covers(gf.createPoint(Coordinate(0, 0, -10))),
            "sanity check of area 1",
        )
        val res1 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    hn.updateBeaconPolygon(offensiveBeacon, world, area1)
                )
            )
        )
        assert(res1.isRight, (res1, "setting first heart's area"))

        val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
        assert(
            area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
            "sanity check of area 2",
        )
        val res2 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    hn.updateBeaconPolygon(defensiveBeacon, world, area2)
                )
            )
        )
        assert(res2.isRight, (res2, "setting second heart's area"))

        hooks.expectSpawn()
        val battle = sql
            .useBlocking(
                sql.withS(
                    sql.withTX(
                        battleManager.startBattle(
                            offensiveBeacon,
                            defensiveBeacon,
                            area2,
                            area2,
                            world.getUID(),
                        )
                    )
                )
            )
            .toOption
            .get

        for _ <- 1 to battleManager.initialHealth - 1 do
            hooks.expectSpawn()
            sql.useBlocking(sql.withS(battleManager.pillarTaken(battle)))

        hooks.expectTaken()
        sql.useBlocking(sql.withS(battleManager.pillarTaken(battle)))

        sql.useBlocking(
            sql.withS(
                sql.withTX(hn.removeHeart(defensiveLocation, defensiveOwner))
            )
        )
        sql.useBlocking(
            sql.withS(
                sql.withTX(hn.removeHeart(offensiveLocation, offensiveOwner))
            )
        )
    }
    sql.test("battle manager offensive heart dies during battle") {
        implicit sql =>
            given gm: GroupManager = GroupManager()
            given it: Deferred[IO, BeaconManagerHooks] =
                sql.useBlocking(Deferred[IO, BeaconManagerHooks])
            given hn: CivBeaconManager = CivBeaconManager()
            given hooks: TestBattleHooks = TestBattleHooks(using this)
            given clock: TestClock = TestClock(OffsetDateTime.now())
            given PrimeTimeManager = PrimeTimeManager()
            given battleManager: BattleManager = BattleManager()
            sql.useBlocking(it.complete(IngameBeaconManagerHooks()))
            val world = WorldMock()

            val offset = 2 * 10

            val offensiveOwner = UUID.randomUUID()
            val offensiveLocation = Location(world, 0, -10, 0)
            val (offensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(hn.placeHeart(offensiveLocation, offensiveOwner))
                ).toOption
                    .get

            val defensiveOwner = UUID.randomUUID()
            val defensiveLocation = Location(world, offset, 10, offset)
            val (defensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(
                        hn.placeHeart(
                            defensiveLocation,
                            defensiveOwner,
                        )
                    )
                ).toOption
                    .get

            clock.changeTimeBy(Duration.ofDays(4))

            assertNotEquals(
                offensiveBeacon,
                defensiveBeacon,
                "different heart IDs",
            )

            val gf = GeometryFactory()
            val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
            assert(
                area1.covers(gf.createPoint(Coordinate(0, 0, -10))),
                "sanity check of area 1",
            )
            val res1 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(offensiveBeacon, world, area1)
                    )
                )
            )
            assert(res1.isRight, (res1, "setting first heart's area"))

            val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
            assert(
                area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
                "sanity check of area 2",
            )
            val res2 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(defensiveBeacon, world, area2)
                    )
                )
            )
            assert(res2.isRight, (res2, "setting second heart's area"))

            hooks.expectSpawn()
            val battle = sql
                .useBlocking(
                    sql.withS(
                        sql.withTX(
                            battleManager.startBattle(
                                offensiveBeacon,
                                defensiveBeacon,
                                area2,
                                area2,
                                world.getUID(),
                            )
                        )
                    )
                )
                .toOption
                .get

            for _ <- 1 to battleManager.initialHealth - 1 do
                hooks.expectSpawn()
                sql.useBlocking(sql.withS(battleManager.pillarTaken(battle)))

            val sizeBefore = hooks.expectDefended().size
            sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.removeHeart(offensiveLocation, offensiveOwner)
                    )
                )
            )
            assertNotEquals(
                sizeBefore,
                hooks.defendQueue.size,
                "a battle should've been defended",
            )
    }
    sql.test("battle manager defensive heart dies during battle") {
        implicit sql =>
            given gm: GroupManager = GroupManager()
            given it: Deferred[IO, BeaconManagerHooks] =
                sql.useBlocking(Deferred[IO, BeaconManagerHooks])
            given hn: CivBeaconManager = CivBeaconManager()
            given hooks: TestBattleHooks = TestBattleHooks(using this)
            given clock: TestClock = TestClock(OffsetDateTime.now())
            given PrimeTimeManager = PrimeTimeManager()
            given battleManager: BattleManager = BattleManager()
            sql.useBlocking(it.complete(IngameBeaconManagerHooks()))
            val world = WorldMock()

            val offset = 2 * 10

            val offensiveOwner = UUID.randomUUID()
            val offensiveLocation = Location(world, 0, -10, 0)
            val (offensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(hn.placeHeart(offensiveLocation, offensiveOwner))
                ).toOption
                    .get

            val defensiveOwner = UUID.randomUUID()
            val defensiveLocation = Location(world, offset, 10, offset)
            val (defensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(
                        hn.placeHeart(
                            defensiveLocation,
                            defensiveOwner,
                        )
                    )
                ).toOption
                    .get

            clock.changeTimeBy(Duration.ofDays(4))

            assertNotEquals(
                offensiveBeacon,
                defensiveBeacon,
                "different heart IDs",
            )

            val gf = GeometryFactory()
            val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
            assert(
                area1.covers(gf.createPoint(Coordinate(0, 0, -10))),
                "sanity check of area 1",
            )
            val res1 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(offensiveBeacon, world, area1)
                    )
                )
            )
            assert(res1.isRight, (res1, "setting first heart's area"))

            val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
            assert(
                area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
                "sanity check of area 2",
            )
            val res2 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(defensiveBeacon, world, area2)
                    )
                )
            )
            assert(res2.isRight, (res2, "setting second heart's area"))

            hooks.expectSpawn()
            val battle = sql
                .useBlocking(
                    sql.withS(
                        sql.withTX(
                            battleManager.startBattle(
                                offensiveBeacon,
                                defensiveBeacon,
                                area2,
                                area2,
                                world.getUID(),
                            )
                        )
                    )
                )
                .toOption
                .get

            for _ <- 1 to battleManager.initialHealth - 1 do
                hooks.expectSpawn()
                sql.useBlocking(sql.withS(battleManager.pillarTaken(battle)))

            val sizeBefore = hooks.expectTaken().size
            sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.removeHeart(defensiveLocation, defensiveOwner)
                    )
                )
            )
            assertNotEquals(
                sizeBefore,
                hooks.takeQueue.size,
                "a battle should've been taken",
            )
    }
    sql.test("battle manager rejects battles started outside of prime time") {
        implicit sql =>
            given gm: GroupManager = GroupManager()
            given it: Deferred[IO, BeaconManagerHooks] =
                sql.useBlocking(Deferred[IO, BeaconManagerHooks])
            given hn: CivBeaconManager = CivBeaconManager()
            given hooks: TestBattleHooks = TestBattleHooks(using this)
            given clock: TestClock = TestClock(OffsetDateTime.parse("2023-11-01T06:00:00+00:00"))
            given pm: PrimeTimeManager = PrimeTimeManager()
            given battleManager: BattleManager = BattleManager()
            sql.useBlocking(it.complete(IngameBeaconManagerHooks()))
            val world = WorldMock()

            val offset = 2 * 10

            val offensiveOwner = UUID.randomUUID()
            val offensiveLocation = Location(world, 0, -10, 0)
            val (offensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(hn.placeHeart(offensiveLocation, offensiveOwner))
                ).toOption
                    .get

            val defensiveOwner = UUID.randomUUID()
            val defensiveLocation = Location(world, offset, 10, offset)
            val (defensiveBeacon, _) =
                sql.useBlocking(
                    sql.withS(
                        hn.placeHeart(
                            defensiveLocation,
                            defensiveOwner,
                        )
                    )
                ).toOption
                    .get

            clock.time = OffsetDateTime.parse("2023-12-01T06:00:00+00:00")

            val defensiveGroup = sql.useBlocking(sql.withS(sql.withTX(gm.createGroup(defensiveOwner, "defensive group"))))
            assert(sql.useBlocking(sql.withS(sql.withTX(hn.setGroup(defensiveBeacon, defensiveGroup)))).isRight, "should've been able to bind defensive beacon to group")

            val result = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        pm.setGroupPrimeTime(
                            defensiveOwner,
                            defensiveGroup,
                            OffsetTime.parse("05:00:00+00:00"),
                        )
                    )
                )
            )
            assert(result.isRight, "should've been able to set prime time")

            assertNotEquals(
                offensiveBeacon,
                defensiveBeacon,
                "different heart IDs",
            )

            val gf = GeometryFactory()
            val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
            assert(
                area1.covers(gf.createPoint(Coordinate(0, 0, -10))),
                "sanity check of area 1",
            )
            val res1 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(offensiveBeacon, world, area1)
                    )
                )
            )
            assert(res1.isRight, (res1, "setting first heart's area"))

            val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
            assert(
                area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
                "sanity check of area 2",
            )
            val res2 = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        hn.updateBeaconPolygon(defensiveBeacon, world, area2)
                    )
                )
            )
            assert(res2.isRight, (res2, "setting second heart's area"))

            hooks.expectSpawn()
            val battle = sql
                .useBlocking(
                    sql.withS(
                        sql.withTX(
                            battleManager.startBattle(
                                offensiveBeacon,
                                defensiveBeacon,
                                area2,
                                area2,
                                world.getUID(),
                            )
                        )
                    )
                )
            assert(battle.isRight, "battle shouldn've been started in prime time")

            clock.time = OffsetDateTime.parse("2023-12-03T04:00:00+00:00")
            val battle2 = sql
                .useBlocking(
                    sql.withS(
                        sql.withTX(
                            battleManager.startBattle(
                                offensiveBeacon,
                                defensiveBeacon,
                                area2,
                                area2,
                                world.getUID(),
                            )
                        )
                    )
                )
            assert(battle2.isLeft, "battle shouldn't have been started outside of prime time")
    }
    sql.test("battle manager rejects beacons that are too new") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given it: Deferred[IO, BeaconManagerHooks] = DummyBeaconHooks.it
        given hn: CivBeaconManager = CivBeaconManager()
        given hooks: TestBattleHooks = TestBattleHooks(using this)
        given clock: TestClock = TestClock(OffsetDateTime.now())
        given PrimeTimeManager = PrimeTimeManager()
        given battleManager: BattleManager = BattleManager()
        val world = WorldMock()

        val offset = 2 * 10

        val offensiveOwner = UUID.randomUUID()
        val offensiveLocation = Location(world, 0, -10, 0)
        val (offensiveBeacon, _) =
            sql.useBlocking(
                sql.withS(hn.placeHeart(offensiveLocation, offensiveOwner))
            ).toOption
                .get

        val defensiveOwner = UUID.randomUUID()
        val defensiveLocation = Location(world, offset, 10, offset)
        val (defensiveBeacon, _) =
            sql.useBlocking(
                sql.withS(
                    hn.placeHeart(
                        defensiveLocation,
                        defensiveOwner,
                    )
                )
            ).toOption
                .get

        assertNotEquals(
            offensiveBeacon,
            defensiveBeacon,
            "different heart IDs",
        )

        val gf = GeometryFactory()
        val area1 = rectangleCenteredAt(gf, 0, 0, -10, 5)
        assert(
            area1.covers(gf.createPoint(Coordinate(0, 0, -10))),
            "sanity check of area 1",
        )
        val res1 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    hn.updateBeaconPolygon(offensiveBeacon, world, area1)
                )
            )
        )
        assert(res1.isRight, (res1, "setting first heart's area"))

        val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
        assert(
            area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
            "sanity check of area 2",
        )
        val res2 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    hn.updateBeaconPolygon(defensiveBeacon, world, area2)
                )
            )
        )
        assert(res2.isRight, (res2, "setting second heart's area"))

        val battle = sql
            .useBlocking(
                sql.withS(
                    sql.withTX(
                        battleManager.startBattle(
                            offensiveBeacon,
                            defensiveBeacon,
                            area2,
                            area2,
                            world.getUID(),
                        )
                    )
                )
            )

        assertEquals(battle, Left(BattleError.beaconIsTooNew))
    }