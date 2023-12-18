package BallCore.NoodleEditor

import BallCore.Storage.SQLManager
import BallCore.Beacons.CivBeaconManager
import skunk.implicits._
import BallCore.Storage.Migration
import BallCore.Beacons.OwnerID
import org.bukkit.Location
import skunk.Session
import cats.effect.IO
import skunk.codec.all._
import BallCore.Beacons.BeaconID
import skunk.Transaction
import BallCore.Groups.GroupID

trait EssenceManagerHooks:
    def updateHeart(l: Location, amount: Int): IO[Unit]

class EssenceManager(hooks: EssenceManagerHooks)(using sql: SQLManager, cbm: CivBeaconManager):
    sql.applyMigration(
        Migration(
            "Initial Essence Manager",
            List(
                sql"""
                CREATE TABLE HeartEssence (
                    Count INTEGER NOT NULL,
                    Owner UUID NOT NULL REFERENCES Hearts(OWNER) ON DELETE CASCADE
                );
                """.command,
            ),
            List(
                sql"""
                DROP TABLE HeartEssence;
                """.command,
            ),
        )
    )

    def addEssence(owner: OwnerID, l: Location)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(sql"""
        INSERT INTO HeartEssence (
            Count, Owner
        ) VALUES (
            1, $uuid
        ) ON CONFLICT (Owner) DO UPDATE SET Count = Count + 1
        RETURNING Count;
        """, int4, owner).flatMap { amount =>
            hooks.updateHeart(l, amount)
        }

    def depleteEssenceFor(group: GroupID, amount: Integer)(using Session[IO], Transaction[IO]): IO[Unit] =
        cbm.getHeartsForGroup(group)
