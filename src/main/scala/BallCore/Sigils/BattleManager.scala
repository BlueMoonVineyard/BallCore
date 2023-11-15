package BallCore.Sigils

import BallCore.Storage
import skunk.implicits._
import BallCore.Groups.GroupID
import java.util.UUID
import skunk.Session
import cats.effect.IO
import skunk.codec.all._
import skunk.SqlState
import cats.syntax.traverse.*

type BattleID = UUID

trait BattleHooks:
    def spawnPillarFor(battle: BattleID): IO[Unit]
    def battleWon(battle: BattleID): IO[Unit]
    def battleLost(battle: BattleID): IO[Unit]

class BattleManager(using sql: Storage.SQLManager, hooks: BattleHooks):
    val initialHealth = 6

    sql.applyMigration(
        Storage.Migration(
            "Initial Battle Manager",
            List(
                sql"""
				CREATE TABLE Battles(
					BattleID UUID PRIMARY KEY,
					OffensiveGroup UUID NOT NULL,
					DefensiveGroup UUID NOT NULL,
					Health INTEGER NOT NULL,
					UNIQUE(OffensiveGroup, DefensiveGroup),
                    CHECK(Health BETWEEN 1 AND 10)
				);
				""".command
            ),
            List(),
        )
    )

    private def spawnInitialPillars(battle: BattleID): IO[Unit] =
        (1 to 5).toList.traverse(_ => hooks.spawnPillarFor(battle)).map(_ => ())

    def startBattle(offensive: GroupID, defensive: GroupID)(using
        Session[IO]
    ): IO[BattleID] =
        sql.useIO(
            sql.queryUniqueIO(
                sql"""
            INSERT INTO Battles (
                BattleID, OffensiveGroup, DefensiveGroup, Health
            ) VALUES (
                gen_random_uuid(), $uuid, $uuid, $int4
            ) RETURNING BattleID;
            """,
                uuid,
                (offensive, defensive, initialHealth),
            )).flatTap { battleID =>
                spawnInitialPillars(battleID)
            }
    def pillarDefended(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.useIO(
            sql.commandIO(
                sql"""
        UPDATE Battles SET Health = Health + 1 WHERE BattleID = $uuid;
        """,
                battle,
            ).map(_ => ())
                .redeemWith(
                    { case SqlState.CheckViolation(_) =>
                        sql.commandIO(
                            sql"""
                        DELETE FROM Battles WHERE BattleID = $uuid
                        """,
                            battle,
                        ).flatMap(_ => hooks.battleWon(battle))
                    },
                    { _ =>
                        hooks.spawnPillarFor(battle)
                    },
                )
        )
    def pillarTaken(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.useIO(
            sql.commandIO(
                sql"""
        UPDATE Battles SET Health = Health - 1 WHERE BattleID = $uuid;
        """,
                battle,
            ).map(_ => ())
                .redeemWith(
                    { case SqlState.CheckViolation(_) =>
                        sql.commandIO(
                            sql"""
                        DELETE FROM Battles WHERE BattleID = $uuid
                        """,
                            battle,
                        ).flatMap(_ => hooks.battleLost(battle))
                    },
                    { _ =>
                        hooks.spawnPillarFor(battle)
                    },
                )
        )
