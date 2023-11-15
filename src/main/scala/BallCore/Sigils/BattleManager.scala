package BallCore.Sigils

import BallCore.Storage
import skunk.implicits._
import java.util.UUID
import skunk.Session
import cats.effect.IO
import skunk.codec.all._
import skunk.SqlState
import cats.syntax.traverse.*
import BallCore.Beacons.BeaconID

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
                    OffensiveBeacon UUID NOT NULL,
                    DefensiveBeacon UUID NOT NULL,
                    Health INTEGER NOT NULL,
                    PillarCount INTEGER NOT NULL,
                    FOREIGN KEY (OffensiveBeacon) REFERENCES CivBeacons (ID),
                    FOREIGN KEY (DefensiveBeacon) REFERENCES CivBeacons (ID),
                    UNIQUE(OffensiveBeacon, DefensiveBeacon),
                    CHECK(Health BETWEEN 1 AND 10)
                );
                """.command
            ),
            List(),
        )
    )

    private def spawnInitialPillars(battle: BattleID, count: Int): IO[Unit] =
        (1 to count).toList.traverse(_ => hooks.spawnPillarFor(battle)).map(_ => ())

    def startBattle(offensive: BeaconID, defensive: BeaconID)(using
        Session[IO]
    ): IO[BattleID] =
        sql.useIO(
            sql.queryUniqueIO(
                sql"""
            INSERT INTO Battles (
                BattleID, OffensiveBeacon, DefensiveBeacon, Health, PillarCount
            ) SELECT
                gen_random_uuid() as BattleID,
                $uuid as OffensiveBeacon,
                $uuid as DefensiveBeacon,
                $int4 as Health,
                GREATEST(FLOOR(ST_Area(ST_Intersection(
                    (SELECT CoveredArea FROM CivBeacons WHERE ID = $uuid),
                    (SELECT CoveredArea FROM CivBeacons WHERE ID = $uuid)
                )) / 512.0), 1)  as PillarCount
            RETURNING BattleID, PillarCount;
            """,
                (uuid *: int4),
                (offensive, defensive, initialHealth, offensive, defensive),
            )
        ).flatTap { (battleID, count) =>
            spawnInitialPillars(battleID, count)
        }.map(_._1)
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
