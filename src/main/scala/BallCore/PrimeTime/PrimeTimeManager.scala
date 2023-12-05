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
import cats.data.OptionT

enum PrimeTimeError:
    case groupError(error: GroupError)
    case waitUntilTomorrowWindowHasPassed

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
                """.command,
                sql"""
                CREATE TABLE TemporaryPrimeTimeAdjustmentVulnerabilityWindows (
                    GroupID UUID UNIQUE NOT NULL REFERENCES GroupStates(ID),
                    StartOfTodayWindow TIMESTAMPTZ NOT NULL,
                    StartOfTomorrowWindow TIMESTAMPTZ NOT NULL
                );
                """.command,
            ),
            List(
                sql"""
                DROP TABLE PrimeTimes;
                """.command
            ),
        )
    )

    val windowSize = Duration.ofHours(6)

    private def checkIfExtraWindowIsActive(
        group: GroupID
    )(using Session[IO], Transaction[IO]): IO[Option[Boolean]] =
        (for {
            window <- OptionT(
                sql.queryOptionIO(
                    sql"""
            SELECT StartOfTodayWindow, StartOfTomorrowWindow FROM TemporaryPrimeTimeAdjustmentVulnerabilityWindows
                WHERE GroupID = $uuid;
            """,
                    (timestamptz *: timestamptz),
                    group,
                )
            )
            (startOfToday, startOfTomorrow) = window
            now <- OptionT.liftF(c.nowIO())
            result <-
                if startOfTomorrow.plus(windowSize).isBefore(now) then
                    OptionT.liftF(
                        sql.commandIO(
                            sql"""
                DELETE FROM TemporaryPrimeTimeAdjustmentVulnerabilityWindows WHERE GroupID = $uuid;
                """,
                            group,
                        ).map(_ => false)
                    )
                else
                    OptionT.pure[IO](
                        startOfToday.isBefore(now) && startOfToday
                            .plus(windowSize)
                            .isAfter(now) || startOfTomorrow.isBefore(
                            now
                        ) && startOfTomorrow.plus(windowSize).isAfter(now)
                    )
        } yield result).value

    def setGroupPrimeTime(
        as: UserID,
        group: GroupID,
        time: OffsetTime,
    )(using Session[IO], Transaction[IO]): IO[Either[PrimeTimeError, Unit]] =
        (for {
            _ <- gm
                .checkE(
                    as,
                    group,
                    nullUUID,
                    Permissions.UpdateGroupInformation,
                )
                .leftMap(PrimeTimeError.groupError.apply)
            _ <- EitherT(checkIfExtraWindowIsActive(group).map { x =>
                if x.isDefined then
                    Left(PrimeTimeError.waitUntilTomorrowWindowHasPassed)
                else Right(())
            })
            _ <- EitherT.right(
                sql.queryUniqueIO(
                    sql"""
                SELECT EXISTS (SELECT 1 FROM PrimeTimes WHERE GroupID = $uuid);
                """,
                    bool, group,
                ).flatMap { exists =>
                    if !exists then
                        sql.commandIO(
                            sql"""
                    INSERT INTO PrimeTimes (
                        GroupID, StartOfWindow
                    ) VALUES (
                        $uuid, $timetz
                    );
                    """,
                            (group, time),
                        )
                    else
                        for {
                            current <- sql.queryUniqueIO(
                                sql"""
                            SELECT StartOfWindow FROM PrimeTimes WHERE GroupID = $uuid
                            """,
                                timetz,
                                group,
                            )
                            now <- c.nowIO()
                            tomorrow = now.plusDays(1)
                            currentInSameZoneAsNow = current
                                .withOffsetSameInstant(now.getOffset())
                            todayWindow = currentInSameZoneAsNow
                                .atDate(now.toLocalDate())
                            tomorrowWindow = currentInSameZoneAsNow
                                .atDate(tomorrow.toLocalDate())
                            _ <- sql.commandIO(
                                sql"""
                            INSERT INTO TemporaryPrimeTimeAdjustmentVulnerabilityWindows (
                                GroupID, StartOfTodayWindow, StartOfTomorrowWindow
                            ) VALUES (
                                $uuid, $timestamptz, $timestamptz
                            );
                            """,
                                (group, todayWindow, tomorrowWindow),
                            )
                            _ <- sql.commandIO(
                                sql"""
                            UPDATE PrimeTimes SET StartOfWindow = $timetz WHERE GroupID = $uuid;
                            """,
                                (time, group),
                            )
                        } yield ()
                }
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

    def isGroupInPrimeTime(
        group: GroupID
    )(using Session[IO], Transaction[IO]): IO[Boolean] =
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
            active <- checkIfExtraWindowIsActive(group)
            isExtra = active.getOrElse(false)
        } yield primeTime
            .map { start =>
                val end = start.plus(windowSize)
                isExtra || timeIsBetween(start, end, now)
            }
            .getOrElse(false)
