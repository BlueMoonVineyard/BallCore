// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.TestClock
import BallCore.Storage.SQLManager

import java.time.OffsetDateTime
import BallCore.Fingerprints.FingerprintManager
import cats.effect.IO
import cats.effect.std.Random
import munit.CatsEffectAssertions._
import cats.effect.kernel.Ref
import java.util.UUID
import cats.syntax.all._
import BallCore.Fingerprints.FingerprintReason

class FingerprintSuite extends munit.CatsEffectSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    val players =
        (1 to 10).map(_ => mockServerSingleton.addPlayer()).toList

    sql.test("players have unique and consistent fingerprints") {
        implicit sql =>
            for {
                given Random[IO] <- Random.scalaUtilRandom[IO]
                given TestClock = TestClock(OffsetDateTime.now())
                manager = FingerprintManager()
                set <- Ref[IO].of(Set[String]())
                _ <- sql.withS(
                    sql.withTX(
                        players.traverse(player =>
                            for {
                                fprintA <- manager.fingerprintFor(
                                    player.getUniqueId
                                )
                                fprintB <- manager.fingerprintFor(
                                    player.getUniqueId
                                )
                                _ <- IO { assertEquals(fprintA, fprintB) }
                                before <- set.get
                                after <- set.updateAndGet(x => x + fprintA)
                                _ <- IO { assertNotEquals(before, after) }
                            } yield ()
                        )
                    )
                )
            } yield ()
    }
    sql.test("spatial retrival is working") { implicit sql =>
        val worldID = UUID.randomUUID()
        val fingerprints = 10

        sql.withS(for {
            given Random[IO] <- Random.scalaUtilRandom[IO]
            given TestClock = TestClock(OffsetDateTime.now())
            manager = FingerprintManager()
            _ <-
                sql.withTX(
                    players.traverse(player =>
                        for {
                            _ <- manager.storeFingerprintAt(
                                0,
                                0,
                                0,
                                worldID,
                                player.getUniqueId,
                                FingerprintReason.bustedThrough,
                            )
                        } yield ()
                    )
                )
            fprints = manager.fingerprintsInTheVicinityOf(1, 1, 1, worldID)
            items <- fprints
            _ <- IO { assert(items.size == fingerprints) }
            items2 <- fprints
            _ <- IO { assert(items2.size == 0) }
        } yield ())
    }
}
