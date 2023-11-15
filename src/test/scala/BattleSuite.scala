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

class TestBattleHooks(using assertions: Assertions) extends BattleHooks:
    val spawnQueue: mutable.Queue[Unit] =
        mutable.Queue[Unit]()
    val defendQueue: mutable.Queue[Unit] =
        mutable.Queue[Unit]()
    val takeQueue: mutable.Queue[Unit] =
        mutable.Queue[Unit]()

    def expectSpawn() = spawnQueue.enqueue(())
    override def spawnPillarFor(battle: BattleID, offense: BeaconID, defense: BeaconID): IO[Unit] =
        IO {
            assertions.assert(!spawnQueue.isEmpty, "unexpected spawn pillar")
            spawnQueue.dequeue()
        }
    def expectDefended() = defendQueue.enqueue(())
    override def battleDefended(battle: BattleID, offense: BeaconID, defense: BeaconID): IO[Unit] =
        IO {
            assertions.assert(!defendQueue.isEmpty, "unexpected defended")
            defendQueue.dequeue()
        }
    def expectTaken() = takeQueue.enqueue(())
    override def battleTaken(battle: BattleID, offense: BeaconID, area: Polygon, defense: BeaconID): IO[Unit] =
        IO {
            assertions.assert(!takeQueue.isEmpty, "unexpected taken")
            takeQueue.dequeue()
        }

class BattleSuite extends munit.FunSuite:
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)

    sql.test("battle manager pillar amount") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given hn: CivBeaconManager = CivBeaconManager()
        given hooks: TestBattleHooks = TestBattleHooks(using this)
        given battleManager: BattleManager = BattleManager()
        val world = WorldMock()

        val sizes = List((10, 1), (12, 2))
        sizes.foreach { (size, pillarCount) =>
            val offset = 2 * size

            val offensiveOwner = UUID.randomUUID()
            val offensiveLocation = Location(world, 0, -10, 0)
            val (offensiveBeacon, _) =
                sql.useBlocking(
                    hn.placeHeart(offensiveLocation, offensiveOwner)
                ).toOption
                    .get

            val defensiveOwner = UUID.randomUUID()
            val defensiveLocation = Location(world, offset, 10, offset)
            val (defensiveBeacon, _) =
                sql.useBlocking(
                    hn.placeHeart(
                        defensiveLocation,
                        defensiveOwner,
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
                hn.updateBeaconPolygon(offensiveBeacon, world, area1)
            )
            assert(res1.isRight, (res1, "setting first heart's area"))

            val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
            assert(
                area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
                "sanity check of area 2",
            )
            val res2 = sql.useBlocking(
                hn.updateBeaconPolygon(defensiveBeacon, world, area2)
            )
            assert(res2.isRight, (res2, "setting second heart's area"))

            for _ <- 1 to pillarCount do hooks.expectSpawn()
            val battle = sql.useBlocking(
                battleManager.startBattle(
                    offensiveBeacon,
                    area2,
                    defensiveBeacon,
                )
            )

            hooks.expectDefended()
            sql.useBlocking(battleManager.offensiveResign(battle))

            sql.useBlocking(hn.removeHeart(defensiveLocation, defensiveOwner))
            sql.useBlocking(hn.removeHeart(offensiveLocation, offensiveOwner))
        }
    }
    sql.test("battle manager defense") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given hn: CivBeaconManager = CivBeaconManager()
        given hooks: TestBattleHooks = TestBattleHooks(using this)
        given battleManager: BattleManager = BattleManager()
        val world = WorldMock()

        val offset = 2 * 10

        val offensiveOwner = UUID.randomUUID()
        val offensiveLocation = Location(world, 0, -10, 0)
        val (offensiveBeacon, _) =
            sql.useBlocking(
                hn.placeHeart(offensiveLocation, offensiveOwner)
            ).toOption
                .get

        val defensiveOwner = UUID.randomUUID()
        val defensiveLocation = Location(world, offset, 10, offset)
        val (defensiveBeacon, _) =
            sql.useBlocking(
                hn.placeHeart(
                    defensiveLocation,
                    defensiveOwner,
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
            hn.updateBeaconPolygon(offensiveBeacon, world, area1)
        )
        assert(res1.isRight, (res1, "setting first heart's area"))

        val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
        assert(
            area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
            "sanity check of area 2",
        )
        val res2 = sql.useBlocking(
            hn.updateBeaconPolygon(defensiveBeacon, world, area2)
        )
        assert(res2.isRight, (res2, "setting second heart's area"))

        hooks.expectSpawn()
        val battle = sql.useBlocking(
            battleManager.startBattle(
                offensiveBeacon,
                area2,
                defensiveBeacon,
            )
        )

        for _ <- battleManager.initialHealth+1 to 10 do
            hooks.expectSpawn()
            sql.useBlocking(battleManager.pillarDefended(battle))

        hooks.expectDefended()
        sql.useBlocking(battleManager.pillarDefended(battle))

        sql.useBlocking(hn.removeHeart(defensiveLocation, defensiveOwner))
        sql.useBlocking(hn.removeHeart(offensiveLocation, offensiveOwner))
    }
    sql.test("battle manager conquest") { implicit sql =>
        given gm: GroupManager = GroupManager()
        given hn: CivBeaconManager = CivBeaconManager()
        given hooks: TestBattleHooks = TestBattleHooks(using this)
        given battleManager: BattleManager = BattleManager()
        val world = WorldMock()

        val offset = 2 * 10

        val offensiveOwner = UUID.randomUUID()
        val offensiveLocation = Location(world, 0, -10, 0)
        val (offensiveBeacon, _) =
            sql.useBlocking(
                hn.placeHeart(offensiveLocation, offensiveOwner)
            ).toOption
                .get

        val defensiveOwner = UUID.randomUUID()
        val defensiveLocation = Location(world, offset, 10, offset)
        val (defensiveBeacon, _) =
            sql.useBlocking(
                hn.placeHeart(
                    defensiveLocation,
                    defensiveOwner,
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
            hn.updateBeaconPolygon(offensiveBeacon, world, area1)
        )
        assert(res1.isRight, (res1, "setting first heart's area"))

        val area2 = rectangleCenteredAt(gf, offset, offset, 10, 5)
        assert(
            area2.covers(gf.createPoint(Coordinate(offset, offset, 10))),
            "sanity check of area 2",
        )
        val res2 = sql.useBlocking(
            hn.updateBeaconPolygon(defensiveBeacon, world, area2)
        )
        assert(res2.isRight, (res2, "setting second heart's area"))

        hooks.expectSpawn()
        val battle = sql.useBlocking(
            battleManager.startBattle(
                offensiveBeacon,
                area2,
                defensiveBeacon,
            )
        )

        for _ <- 1 to battleManager.initialHealth-1 do
            hooks.expectSpawn()
            sql.useBlocking(battleManager.pillarTaken(battle))

        hooks.expectTaken()
        sql.useBlocking(battleManager.pillarTaken(battle))

        sql.useBlocking(hn.removeHeart(defensiveLocation, defensiveOwner))
        sql.useBlocking(hn.removeHeart(offensiveLocation, offensiveOwner))
    }
