// SPDX-FileCopyrightText: 2024 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import CivCubed.Storage.SQLManager
import cats.effect.IO
import java.util.UUID
import CivCubed.PVPServer.LoadoutManager

class LoadoutSuite extends munit.CatsEffectSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)

    sql.test("players can create and fetch loadouts") { implicit sql =>
        for {
            uuid <- IO { UUID.randomUUID() }
            uuid2 <- IO { UUID.randomUUID() }
            lm = LoadoutManager()

            _ <- sql.withS(lm.setLoadout(uuid, "First", Array()))
            _ <- sql.withS(lm.setLoadout(uuid, "Second", Array()))

            items <- sql.withS(lm.getLoadouts(uuid))

            _ <- IO.pure(items.length).assertEquals(2)
            _ <- IO.pure(items).assert(_.contains("First"))
            _ <- IO.pure(items).assert(_.contains("Second"))

            _ <- sql.withS(lm.getLoadout(uuid, "First")).assert(_.isDefined)
            _ <- sql.withS(lm.getLoadout(uuid, "Second")).assert(_.isDefined)
            _ <- sql.withS(lm.getLoadout(uuid2, "Second")).assert(_.isEmpty)
        } yield ()
    }
    sql.test("players can create and publish public loadouts") { implicit sql =>
        for {
            uuid <- IO { UUID.randomUUID() }
            lm = LoadoutManager()
            _ <- sql.withS(lm.setLoadout(uuid, "First", Array()))
            _ <- sql.withS(lm.setLoadout(uuid, "Second", Array()))
            _ <- sql
                .withS(lm.publishLoadout(uuid, "First", "Waow"))
                .assert(_.isRight)
            _ <- sql
                .withS(lm.publishLoadout(uuid, "Second", "Waow"))
                .assert(_.isLeft)
            _ <- sql
                .withS(lm.publishLoadout(uuid, "Second", "Second"))
                .assert(_.isRight)

            items <- sql.withS(lm.getPublicLoadouts())

            _ <- IO.pure(items.length).assertEquals(2)
            _ <- IO.pure(items).assert(_.contains("Waow"))
            _ <- IO.pure(items).assert(_.contains("Second"))

            _ <- sql.withS(lm.getPublicLoadout("Waow")).assert(_.isDefined)
            _ <- sql.withS(lm.getPublicLoadout("Second")).assert(_.isDefined)
            _ <- sql.withS(lm.getPublicLoadout("First")).assert(_.isEmpty)
        } yield ()
    }
}
