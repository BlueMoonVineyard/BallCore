// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.{Clock, TestClock}
import BallCore.Groups.nullUUID
import BallCore.Reinforcements.{
    ReinforcementError,
    ReinforcementState,
    ReinforcementTypes,
}
import BallCore.Storage.SQLManager
import BallCore.{Groups, Reinforcements, Storage}

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util as ju

class ReinforcementSuite extends munit.FunSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    sql.test("block reinforcements basic stuff") { implicit sql =>
        given gm: Groups.GroupManager = new Groups.GroupManager

        given csm: Reinforcements.ChunkStateManager =
            new Reinforcements.ChunkStateManager

        given clock: Clock = new TestClock(OffsetDateTime.now())

        given rm: Reinforcements.BlockReinforcementManager =
            new Reinforcements.BlockReinforcementManager

        val u1 = ju.UUID.randomUUID()
        val u2 = ju.UUID.randomUUID()
        val world = ju.UUID.randomUUID()

        val gid = sql.useBlocking(sql.withTX(gm.createGroup(u1, "test")))
        sql.useBlocking(sql.withTX(gm.addToGroup(u2, gid).value))

        val res1 = rm.reinforce(
            u2,
            gid,
            nullUUID,
            0,
            0,
            0,
            world,
            ReinforcementTypes.IronLike,
        )
        assert(
            res1 == Left(
                Reinforcements.ReinforcementGroupError(
                    Groups.GroupError.NoPermissions
                )
            ),
            res1,
        )

        val res2 = rm.reinforce(
            u1,
            gid,
            nullUUID,
            0,
            0,
            0,
            world,
            ReinforcementTypes.IronLike,
        )
        assert(res2 == Right(()), res2)

        val rid = sql
            .useBlocking(sql.withTX(gm.roles(gid).value))
            .getOrElse(List())
            .find { x => x.name == "Admin" }
            .get
            .id
        assert(
            sql.useBlocking(
                sql.withTX(gm.assignRole(u1, u2, gid, rid, true).value)
            ).isRight
        )

        val res3 = rm.reinforce(
            u2,
            gid,
            nullUUID,
            0,
            0,
            0,
            world,
            ReinforcementTypes.IronLike,
        )
        assert(res3 == Left(Reinforcements.AlreadyExists()), res3)

        val res4 = rm.unreinforce(u2, 0, 0, 0, world)
        assert(res4 == Right(()), res4)

        val res5 = rm.unreinforce(u2, 0, 0, 0, world)
        assert(res5 == Left(Reinforcements.DoesntExist()))

        csm.evictAll()
    }
    sql.test("block breaking shenanigans") { implicit sql =>
        given gm: Groups.GroupManager = new Groups.GroupManager

        given csm: Reinforcements.ChunkStateManager =
            new Reinforcements.ChunkStateManager

        given clock: TestClock = new TestClock(OffsetDateTime.now())

        given rm: Reinforcements.BlockReinforcementManager =
            new Reinforcements.BlockReinforcementManager

        val u1 = ju.UUID.randomUUID()
        val u2 = ju.UUID.randomUUID()
        val world = ju.UUID.randomUUID()

        val gid = sql.useBlocking(sql.withTX(gm.createGroup(u1, "test")))
        sql.useBlocking(sql.withTX(gm.addToGroup(u2, gid).value))

        val res1 = rm.reinforce(
            u1,
            gid,
            nullUUID,
            0,
            0,
            0,
            world,
            ReinforcementTypes.IronLike,
        )
        assert(res1 == Right(()), res1)

        val res2 = rm.break(0, 0, 0, 50.0, world)
        assert(res2.isRight, res2)

        val res3 = rm.break(0, 0, 0, 50.0, world)
        assert(res3.isRight, res3)

        val d1 = res2.toOption.get.health - res3.toOption.get.health

        clock.changeTimeBy(ChronoUnit.HOURS.getDuration.multipliedBy(5))

        val res4 = rm.break(0, 0, 0, 50.0, world)
        assert(res4.isRight, res4)

        val d2 = res3.toOption.get.health - res4.toOption.get.health

        assert(d2 < d1, (d2, d1))

        csm.evictAll()
    }
    sql.test("block break and replace") { implicit sql =>
        given gm: Groups.GroupManager = new Groups.GroupManager

        given csm: Reinforcements.ChunkStateManager =
            new Reinforcements.ChunkStateManager

        given clock: TestClock = new TestClock(OffsetDateTime.now())

        given rm: Reinforcements.BlockReinforcementManager =
            new Reinforcements.BlockReinforcementManager

        val u1 = ju.UUID.randomUUID()
        val u2 = ju.UUID.randomUUID()
        val world = ju.UUID.randomUUID()

        val gid = sql.useBlocking(sql.withTX(gm.createGroup(u1, "test")))
        sql.useBlocking(sql.withTX(gm.addToGroup(u2, gid).value))

        val res1 =
            rm.reinforce(
                u1,
                gid,
                nullUUID,
                0,
                0,
                0,
                world,
                ReinforcementTypes.Stone,
            )
        assert(res1 == Right(()), res1)

        def break(
            cond: Either[ReinforcementError, ReinforcementState] => Boolean
        ): Unit =
            val res = rm.break(0, 0, 0, 5.0, world)
            assert(cond(res), res)

        // if reinforcement damage logic changes, make sure to change this so that
        // it breaks the block successfully
        for i <- 1 to 12 do break(_.isRight)
        break(_.isLeft)

        val res2 =
            rm.reinforce(
                u1,
                gid,
                nullUUID,
                0,
                0,
                0,
                world,
                ReinforcementTypes.Stone,
            )
        assert(res2 == Right(()), res2)

        // ditto
        for i <- 1 to 12 do break(_.isRight)
        break(_.isLeft)

        csm.evictAll()
    }
}
