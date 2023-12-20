package BallCore.NoodleEditor

import BallCore.Storage.SQLManager
import BallCore.Storage.Migration
import skunk.codec.all._
import skunk.implicits._
import BallCore.DataStructures.Clock
import BallCore.Groups.GroupID
import scala.collection.concurrent.TrieMap
import java.time.OffsetDateTime
import cats.effect.IO
import java.time.temporal.ChronoUnit
import skunk.Session
import BallCore.Groups.GroupManager
import BallCore.WebHooks.WebHookManager
import skunk.Transaction
import BallCore.TextComponents._

class DelinquencyManager(using
    sql: SQLManager,
    c: Clock,
    gm: GroupManager,
    webhooks: WebHookManager,
):
    sql.applyMigration(
        Migration(
            "Initial Delinquency Manager",
            List(sql"""
            CREATE TABLE DelinquencyManager (
                GroupID UUID NOT NULL UNIQUE REFERENCES GroupStates(ID) ON DELETE CASCADE,
                DelinquentSince TIMESTAMPTZ
            )
            """.command),
            List(sql"""
            DROP TABLE DelinquencyManager;
            """.command),
        )
    )

    private val cache = TrieMap[GroupID, OffsetDateTime]()

    sql.useBlocking(sql.withS(for
        stream <- sql.queryStreamIO(
            sql"""
            SELECT GroupID, DelinquentSince FROM DelinquencyManager;
            """,
            (uuid *: timestamptz),
            skunk.Void,
        )
        _ <- stream
            .foreach { (group, time) => IO { cache(group) = time } }
            .compile
            .drain
    yield ()))

    def failedToPay(
        group: GroupID,
        cost: Int,
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for
            now <- c.nowIO()
            delinquentSince <- sql.queryUniqueIO(
                sql"""
            INSERT INTO DelinquencyManager (
                GroupID, DelinquentSince
            ) VALUES (
                $uuid, $timestamptz
            ) ON CONFLICT (GroupID) DO UPDATE SET
                    DelinquentSince = DelinquencyManager.DelinquentSince
            RETURNING DelinquentSince;
            """,
                timestamptz,
                (group, now),
            )
            _ <- IO { cache(group) = delinquentSince }
            days <- daysOfGroupDelinquency(group)
            _ <- gm
                .groupAudience(group)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] You've been delinquent on essence payments for $days days. Add essence to your hearts to stop your defenses from weakening! Your current usage needs $cost essence per day."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                group,
                s"You've been delinquent on essence payments for $days days. Add essence to your hearts to stop your defenses from weakening! Your current usage needs $cost essence per day.",
            )
        yield ()

    def paid(
        group: GroupID,
        cost: Int,
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for
            _ <- sql.commandIO(
                sql"""
            DELETE FROM DelinquencyManager
                WHERE GroupID = $uuid;
            """,
                (group),
            )
            _ <- IO { cache.remove(group) }
            _ <- gm
                .groupAudience(group)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] Your arterial claims have drained $cost essence from your group's hearts."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                group,
                s"Your arterial claims have drained $cost essence from your group's hearts.",
            )
        yield ()

    def daysOfGroupDelinquency(group: GroupID): IO[Int] =
        for
            now <- c.nowIO()
            pt <- IO { cache.getOrElse(group, now) }
        yield ChronoUnit.DAYS.between(pt, now).toInt
