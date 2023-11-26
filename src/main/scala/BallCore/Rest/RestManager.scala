package BallCore.Rest

import BallCore.Storage.{Migration, SQLManager}
import cats.effect.IO
import skunk.Session
import skunk.codec.all.*
import skunk.implicits.*
import java.util.UUID
import BallCore.DataStructures.Clock
import cats.data.OptionT
import java.time.temporal.ChronoUnit
import scala.collection.concurrent.TrieMap

trait RestManagerHooks:
    def updateSidebar(playerID: UUID, rest: Double): IO[Unit]

/** Manages rest related tasks and utilities.
  */
class RestManager()(using sql: SQLManager, c: Clock, hooks: RestManagerHooks):
    sql.applyMigration(
        Migration(
            "Initial Rest Manager",
            List(
                sql"""
                    CREATE TABLE RestValues (
                        PlayerID UUID PRIMARY KEY,
                        LoggedOffAt TIMESTAMPTZ,
                        Rest DOUBLE PRECISION NOT NULL DEFAULT 1.0,
                        CHECK(Rest BETWEEN 0.0 AND 1.0)
                    );
                    """.command
            ),
            List(
                sql"""DROP TABLE RestValues;""".command
            ),
        )
    )

    private val restConsumptionAmount = 1.0 / 2000.0
    private val cache = TrieMap[UUID, Double]()
    private def getOrElseUpdateF(
        key: UUID
    )(default: => IO[Double]): IO[Double] =
        OptionT(IO { cache.get(key) }).getOrElseF {
            for {
                value <- default
                _ <- IO { cache.update(key, value) }
            } yield value
        }
    private def updateF(key: UUID, value: Double): IO[Unit] =
        IO {
            cache.update(key, value)
        }
    private def removeF(key: UUID): IO[Unit] =
        IO {
            val _ = cache.remove(key)
        }

    /** Consumes rest, returning if the rest bonus can be applied
      */
    def useRest(playerID: UUID)(using Session[IO]): IO[Boolean] =
        for {
            currentRest <- getOrElseUpdateF(playerID) {
                OptionT(
                    sql.queryOptionIO(
                        sql"SELECT Rest FROM RestValues WHERE PlayerID = $uuid",
                        float8,
                        playerID,
                    )
                ).getOrElse(1.0)
            }
            newRestRaw = currentRest - restConsumptionAmount
            newRest = newRestRaw max 0.0
            _ <- updateF(playerID, newRest)
            _ <- sql
                .commandIO(
                    sql"""
            INSERT INTO RestValues (
                PlayerID, Rest
            ) VALUES (
                $uuid, $float8
            ) ON CONFLICT (PlayerID) DO UPDATE SET Rest = EXCLUDED.Rest;
            """,
                    (playerID, newRest),
                )
            _ <- hooks.updateSidebar(playerID, newRest)
        } yield newRestRaw >= 0

    /** Marks when a player has logged off
      *
      * @param playerId
      *   The player's Minecraft UUID.
      */
    def logoff(playerID: UUID)(using Session[IO]): IO[Unit] =
        for {
            currentTime <- c.nowIO()
            _ <- sql.commandIO(
                sql"""
            INSERT INTO RestValues (
                PlayerID, LoggedOffAt
            ) VALUES (
                $uuid, $timestamptz
            ) ON CONFLICT (PlayerID) DO UPDATE SET LoggedOffAt = EXCLUDED.LoggedOffAt;
            """,
                (playerID, currentTime),
            )
            _ <- removeF(playerID)
        } yield ()

    /** Marks when a player has logged on
      */
    def logon(playerID: UUID)(using Session[IO]): IO[Unit] =
        for {
            oldTime <- OptionT(
                sql.queryOptionIO(
                    sql"""
                    SELECT Rest, LoggedOffAt FROM RestValues WHERE PlayerID = $uuid
                    """,
                    (float8 *: timestamptz),
                    playerID,
                )
            ).getOrElseF {
                for time <- c.nowIO() yield (1.0, time)
            }
            currentTime <- c.nowIO()
            difference = ChronoUnit.MINUTES.between(oldTime._2, currentTime)
            // 1 rest / 12 hours
            // =
            // 1 rest / (12*60) minutes
            restGained = difference.toDouble / (12.0 * 60.0)
            newRest = (oldTime._1 + restGained) min 1.0
            _ <- sql.commandIO(
                sql"""
                INSERT INTO RestValues (
                    PlayerID, LoggedOffAt, Rest
                ) VALUES (
                    $uuid, $timestamptz, $float8
                ) ON CONFLICT (PlayerID) DO UPDATE SET
                    Rest = EXCLUDED.Rest;
            """,
                (playerID, currentTime, newRest),
            )
            _ <- hooks.updateSidebar(playerID, newRest)
        } yield ()
