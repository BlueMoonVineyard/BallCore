// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage.SQLManager
import org.bukkit.entity.Player
import BallCore.OneTimeTeleport.OneTimeTeleporterHooks
import cats.effect.IO
import BallCore.OneTimeTeleport.OneTimeTeleporter
import BallCore.OneTimeTeleport.OTTError

class TestOneTimeTeleporterHooks(var it: Boolean) extends OneTimeTeleporterHooks:
    override def teleport(source: Player, destination: Player): IO[Boolean] =
        IO.pure(it)

class OTTSuite extends munit.FunSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    sql.test("basic functionality") { implicit sql =>
        val hooks = TestOneTimeTeleporterHooks(true)
        val ott = OneTimeTeleporter(hooks)

        val establishedPlayer = mockServerSingleton.addPlayer()
        val joinedPlayer = mockServerSingleton.addPlayer()

        val res1 = sql.useBlocking(ott.requestTeleportTo(joinedPlayer, establishedPlayer))
        assertEquals(res1, Right(()), "the initial request should be fine")

        val res2 = sql.useBlocking(ott.acceptTeleportOf(establishedPlayer, joinedPlayer))
        assertEquals(res2, Right(()), "the accepting should be fine")

        val res2_1 = sql.useBlocking(ott.acceptTeleportOf(establishedPlayer, joinedPlayer))
        assertEquals(res2_1, Left(OTTError.isNotTeleportingToYou), "shouldn't do multiple times")

        val someOtherPlayer = mockServerSingleton.addPlayer()

        val res3 = sql.useBlocking(ott.requestTeleportTo(joinedPlayer, someOtherPlayer))
        assertEquals(res3, Left(OTTError.alreadyUsedTeleport), "you used your one teleport")

        val res4 = sql.useBlocking(ott.acceptTeleportOf(joinedPlayer, someOtherPlayer))
        assertEquals(res4, Left(OTTError.isNotTeleportingToYou), "they weren't going to teleport to you")

        hooks.it = false

        val res5 = sql.useBlocking(ott.requestTeleportTo(someOtherPlayer, joinedPlayer))
        assertEquals(res5, Right(()), "requesting a teleport")

        val res6 = sql.useBlocking(ott.acceptTeleportOf(joinedPlayer, someOtherPlayer))
        assertEquals(res6, Left(OTTError.teleportFailed), "the teleport failed")
    }
}
