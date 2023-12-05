package BallCore.PrimeTime

import BallCore.Storage.SQLManager
import skunk.codec.all._
import skunk.implicits._
import BallCore.Groups.GroupManager
import BallCore.Groups.GroupID
import skunk.Session
import BallCore.DataStructures.Clock
import BallCore.Groups.UserID
import java.time.OffsetDateTime
import skunk.Transaction
import BallCore.Groups.GroupError
import BallCore.Groups.nullUUID
import BallCore.Groups.Permissions
import cats.effect.IO
import BallCore.Storage.Migration
import java.time.OffsetTime
import java.time.Duration
import cats.data.EitherT

class PrimeTimeManager(using sql: SQLManager, gm: GroupManager, c: Clock):
    sql.applyMigration(
        Migration(
            "Initial Prime Time Manager",
            List(
                sql"""
                CREATE TABLE PrimeTimes (
                    GroupID UUID UNIQUE NOT NULL REFERENCES GroupStates(ID),
                    StartOfWindow TIMETZ NOT NULL
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE PrimeTimes;
                """.command
            ),
        )
    )

    val windowSize = Duration.ofHours(6)

    def setGroupPrimeTime(
        as: UserID,
        group: GroupID,
        time: OffsetTime,
    )(using Session[IO], Transaction[IO]): IO[Either[GroupError, Unit]] =
        (for {
            _ <- gm.checkE(
                as,
                group,
                nullUUID,
                Permissions.UpdateGroupInformation,
            )
            _ <- EitherT.right(
                sql.commandIO(
                    sql"""
            INSERT INTO PrimeTimes (
                GroupID, StartOfWindow
            ) VALUES (
                $uuid, $timetz
            ) ON CONFLICT (GroupID) DO UPDATE SET StartOfWindow = EXCLUDED.StartOfWindow;
            """,
                    (group, time),
                )
            )
        } yield ()).value

    private def timeIsBetween(
        start: OffsetTime,
        end: OffsetTime,
        point: OffsetDateTime,
    ): Boolean =
        val pointTime = point.toOffsetTime()
        if start.isAfter(end) then
            pointTime.isAfter(start) || pointTime.isBefore(end)
        else pointTime.isAfter(start) && pointTime.isBefore(end)

    def isGroupInPrimeTime(group: GroupID)(using Session[IO]): IO[Boolean] =
        for {
            now <- c.nowIO()
            primeTime <- sql.queryOptionIO(
                sql"""
            SELECT StartOfWindow FROM PrimeTimes
                WHERE GroupID = $uuid;
            """,
                timetz,
                group,
            )
        } yield primeTime
            .map { start =>
                val end = start.plus(windowSize)
                timeIsBetween(start, end, now)
            }
            .getOrElse(false)
