package BallCore.Rest

import BallCore.Storage.{Migration, SQLManager}
import cats.effect.IO
import skunk.Session
import skunk.codec.all.*
import skunk.implicits.*

import java.util as ju
import java.util.UUID

val DEFAULT_REST = 1.0

/** Manages rest related tasks and utilities.
  */
class RestManager()(using sql: SQLManager):
    sql.applyMigration(
        Migration(
            "Initial Rest Manager",
            List(
                sql"""
                    CREATE TABLE RestValues (
                        PlayerID UUID NOT NULL,
                        Rest DOUBLE PRECISION NOT NULL,
                        UNIQUE(PlayerID)
                    );
                    """.command
            ),
            List(
                sql"""DROP TABLE RestValues;""".command
            ),
        )
    )

    /** Stores a player's rest value in the database.
      *
      * @param playerId
      *   The player's Minecraft UUID.
      * @param rest
      *   The player's rest value.
      * @param session
      *   A connection to the database.
      */
    def save(playerId: UUID, rest: Double)(using
        session: Session[IO]
    ): IO[Unit] =
        sql.commandIO(
            sql"""
            INSERT INTO RestValues (PlayerID, Rest)
            VALUES ($uuid, $float8)
            ON CONFLICT (PlayerID) DO UPDATE SET Rest = EXCLUDED.Rest;""",
            (playerId, rest),
        ).map(_ => ())

    /** Gets the player's rest value from the database.
      *
      * @param playerId
      *   The player's Minecraft UUID.
      * @return
      *   The player's rest value.
      */
    def getPlayerRest(playerId: UUID): Double =
        sql.useBlocking(
            sql.queryOptionIO(
                sql"""SELECT Rest FROM RestValues WHERE PlayerID = $uuid;""",
                float8,
                playerId,
            )
        ) match
            case None =>
                sql.useBlocking(save(playerId, DEFAULT_REST))
                DEFAULT_REST
            case Some(int) => int
