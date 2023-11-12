// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.{Clock, TestClock}
import BallCore.Groups.nullUUID
import BallCore.Reinforcements.ReinforcementTypes
import BallCore.Storage.SQLManager
import BallCore.{Groups, Reinforcements, Storage}

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util as ju

class EntityReinforcementSuite extends munit.FunSuite {
  val sql: FunFixture[SQLManager] = FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
  sql.test("entity reinforcements basic stuff") { implicit sql =>
    given gm: Groups.GroupManager = new Groups.GroupManager

    given csm: Reinforcements.ChunkStateManager =
      new Reinforcements.ChunkStateManager

    given esm: Reinforcements.EntityStateManager =
      new Reinforcements.EntityStateManager

    given clock: Clock = new TestClock(OffsetDateTime.now())

    given erm: Reinforcements.EntityReinforcementManager =
      new Reinforcements.EntityReinforcementManager

    val u1 = ju.UUID.randomUUID()
    val u2 = ju.UUID.randomUUID()
    val entity = ju.UUID.randomUUID()

    val gid = sql.useBlocking(gm.createGroup(u1, "test"))
    sql.useBlocking(gm.addToGroup(u2, gid).value)

    val res1 =
      erm.reinforce(u2, gid, nullUUID, entity, ReinforcementTypes.IronLike)
    assert(
      res1 == Left(
        Reinforcements.ReinforcementGroupError(Groups.GroupError.NoPermissions)
      ),
      res1
    )

    val res2 =
      erm.reinforce(u1, gid, nullUUID, entity, ReinforcementTypes.IronLike)
    assert(res2 == Right(()), res2)

    val rid = sql
      .useBlocking(gm.roles(gid).value)
      .getOrElse(List())
      .find { x => x.name == "Admin" }
      .get
      .id
    assert(sql.useBlocking(gm.assignRole(u1, u2, gid, rid, true).value).isRight)

    val res3 =
      erm.reinforce(u2, gid, nullUUID, entity, ReinforcementTypes.IronLike)
    assert(res3 == Left(Reinforcements.AlreadyExists()), res3)

    val res4 = erm.unreinforce(u2, entity)
    assert(res4 == Right(()), res4)

    val res5 = erm.unreinforce(u2, entity)
    assert(res5 == Left(Reinforcements.DoesntExist()))
  }
  sql.test("entity damaging shenanigans") { implicit sql =>
    given gm: Groups.GroupManager = new Groups.GroupManager

    given csm: Reinforcements.ChunkStateManager =
      new Reinforcements.ChunkStateManager

    given esm: Reinforcements.EntityStateManager =
      new Reinforcements.EntityStateManager

    given clock: TestClock = new TestClock(OffsetDateTime.now())

    given erm: Reinforcements.EntityReinforcementManager =
      new Reinforcements.EntityReinforcementManager

    val u1 = ju.UUID.randomUUID()
    val u2 = ju.UUID.randomUUID()
    val entity = ju.UUID.randomUUID()

    val gid = sql.useBlocking(gm.createGroup(u1, "test"))
    sql.useBlocking(gm.addToGroup(u2, gid).value)

    val res1 =
      erm.reinforce(u1, gid, nullUUID, entity, ReinforcementTypes.IronLike)
    assert(res1 == Right(()), res1)

    val res2 = erm.damage(entity)
    assert(res2.isRight, res2)

    val res3 = erm.damage(entity)
    assert(res3.isRight, res3)

    val d1 = res2.toOption.get.health - res3.toOption.get.health

    clock.changeTimeBy(ChronoUnit.HOURS.getDuration.multipliedBy(5))

    val res4 = erm.damage(entity)
    assert(res4.isRight, res4)

    val d2 = res3.toOption.get.health - res4.toOption.get.health

    assert(d2 < d1, (d2, d1))

    csm.evictAll()
  }
}
