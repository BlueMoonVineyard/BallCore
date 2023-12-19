// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage.SQLManager
import BallCore.Beacons.CivBeaconManager
import BallCore.DataStructures.TestClock
import java.time.OffsetDateTime
import BallCore.Beacons.BeaconManagerHooks
import cats.effect.kernel.Deferred
import cats.effect.IO
import BallCore.Groups.GroupManager
import be.seeseemelk.mockbukkit.WorldMock
import org.bukkit.Location
import java.util.UUID
import BallCore.NoodleEditor.EssenceManager
import BallCore.NoodleEditor.EssenceManagerHooks

class DummyEssenceManagerHooks extends EssenceManagerHooks:
    override def updateHeart(l: Location, amount: Int): IO[Unit] =
        IO.pure(())

class EssenceSuite extends munit.CatsEffectSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)

    val world = WorldMock()
    mockServerSingleton.addWorld(world)
    def loc(x: Int, y: Int, z: Int): Location =
        Location(world, x, y, z)

    def uuid: IO[UUID] =
        IO { UUID.randomUUID() }

    sql.test("basic depletion works as expected") { implicit sql =>
        given TestClock = TestClock(OffsetDateTime.now())
        given Deferred[IO, BeaconManagerHooks] = DummyBeaconHooks.it
        given gm: GroupManager = GroupManager()
        given cbm: CivBeaconManager = CivBeaconManager()
        given em: EssenceManager = EssenceManager(DummyEssenceManagerHooks())
        em

        sql.withS(
            sql.withTX(
                for
                    owner1 <- uuid
                    beacon <- cbm
                        .placeHeart(loc(0, 0, 0), owner1)
                        .map(_.toOption.get._1)
                    group <- gm.createGroup(owner1, "test")
                    _ <- cbm.setGroup(beacon, group).assert(_.isRight)

                    owner2 <- uuid
                    _ <- cbm.placeHeart(loc(0, 0, 1), owner2).assert(_.isRight)

                    owner3 <- uuid
                    _ <- cbm.placeHeart(loc(0, 0, 2), owner3).assert(_.isRight)

                    owner4 <- uuid
                    _ <- cbm.placeHeart(loc(0, 0, 3), owner4).assert(_.isRight)

                    owner5 <- uuid
                    _ <- cbm.placeHeart(loc(0, 0, 4), owner5).assert(_.isRight)

                    _ <- em.addEssence(owner1, loc(0, 0, 0)).replicateA_(5)

                    _ <- em
                        .depleteEssenceFor(group, 5)
                        .assertEquals(
                            0,
                            "all essence should be depleted from one guy",
                        )

                    _ <- em
                        .depleteEssenceFor(group, 5)
                        .assertEquals(
                            5,
                            "no more essence afterwards",
                        )

                    _ <- em.addEssence(owner1, loc(0, 0, 0))
                    _ <- em.addEssence(owner2, loc(0, 0, 1))
                    _ <- em.addEssence(owner3, loc(0, 0, 2))
                    _ <- em.addEssence(owner4, loc(0, 0, 3))
                    _ <- em.addEssence(owner5, loc(0, 0, 4))

                    _ <- em
                        .depleteEssenceFor(group, 5)
                        .assertEquals(
                            0,
                            "all essence should be depleted from the group",
                        )
                    _ <- em
                        .depleteEssenceFor(group, 5)
                        .assertEquals(
                            5,
                            "no more essence afterwards",
                        )

                    _ <- em.addEssence(owner1, loc(0, 0, 0)).replicateA_(3)
                    _ <- em.addEssence(owner2, loc(0, 0, 1)).replicateA_(2)

                    _ <- em
                        .depleteEssenceFor(group, 5)
                        .assertEquals(
                            0,
                            "all essence should be depleted from the group",
                        )
                    _ <- em
                        .depleteEssenceFor(group, 5)
                        .assertEquals(
                            5,
                            "no more essence afterwards",
                        )

                    _ <- em.addEssence(owner1, loc(0, 0, 0)).replicateA_(3)
                    _ <- em.addEssence(owner2, loc(0, 0, 1)).replicateA_(2)

                    _ <- em
                        .depleteEssenceFor(group, 7)
                        .assertEquals(
                            2,
                            "some essence should be depleted from the group",
                        )
                yield ()
            )
        )
    }
}
