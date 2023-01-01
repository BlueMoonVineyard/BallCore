// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage
import BallCore.Groups
import BallCore.Reinforcements
import java.{util => ju}
import org.bukkit.NamespacedKey
import java.util.jar.Attributes.Name

class ReinforcementSuite extends munit.FunSuite {
    test("basic stuff") {
        given sql: Storage.SQLManager = new Storage.SQLManager(test = true)
        given keyVal: Storage.SQLKeyVal = new Storage.SQLKeyVal
        given gm: Groups.GroupManager = new Groups.GroupManager
        given csm: Reinforcements.ChunkStateManager = new Reinforcements.ChunkStateManager
        given rm: Reinforcements.ReinforcementManager = new Reinforcements.ReinforcementManager

        val u1 = ju.UUID.randomUUID()
        val u2 = ju.UUID.randomUUID()
        val world = NamespacedKey("manka", "aknam")

        val gid = gm.createGroup(u1, "test")
        gm.addToGroup(u2, gid)

        val res1 = rm.reinforce(u2, gid, 0, 0, 0, world, 500)
        assert(res1 == Left(Reinforcements.ReinforcementGroupError(Groups.GroupError.NoPermissions)), res1)

        val res2 = rm.reinforce(u1, gid, 0, 0, 0, world, 500)
        assert(res2 == Right(()), res2)

        val rid = gm.roles(gid).getOrElse(List()).find { x => x.name == "Admin" }.get.id
        assert(gm.assignRole(u1, u2, gid, rid, true).isRight)

        val res3 = rm.reinforce(u2, gid, 0, 0, 0, world, 500)
        assert(res3 == Left(Reinforcements.AlreadyExists()), res3)

        val res4 = rm.unreinforce(u2, gid, 0, 0, 0, world)
        assert(res4 == Right(()), res4)

        val res5 = rm.unreinforce(u2, gid, 0, 0, 0, world)
        assert(res5 == Left(Reinforcements.DoesntExist()))
    }
}