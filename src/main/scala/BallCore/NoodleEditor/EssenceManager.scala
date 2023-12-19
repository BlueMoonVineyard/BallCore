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
import skunk.Transaction
import BallCore.Groups.GroupID
import cats.syntax.all._
import org.bukkit.Bukkit

trait EssenceManagerHooks:
    def updateHeart(l: Location, amount: Int): IO[Unit]

class EssenceManager(hooks: EssenceManagerHooks)(using
    sql: SQLManager,
    cbm: CivBeaconManager,
):
    sql.applyMigration(
        Migration(
            "Initial Essence Manager",
            List(
                sql"""
                CREATE TABLE HeartEssence (
                    Count INTEGER NOT NULL,
                    Owner UUID UNIQUE NOT NULL REFERENCES Hearts(OWNER) ON DELETE CASCADE
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE HeartEssence;
                """.command
            ),
        )
    )
    val _ = cbm

    def addEssence(owner: OwnerID, l: Location)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
        INSERT INTO HeartEssence (
            Count, Owner
        ) VALUES (
            1, $uuid
        ) ON CONFLICT (Owner) DO UPDATE SET Count = HeartEssence.Count + 1
        RETURNING Count;
        """,
            int4,
            owner,
        ).flatMap { amount =>
            hooks.updateHeart(l, amount)
        }

    def depleteEssenceFor(group: GroupID, amount: Integer)(using
        Session[IO],
        Transaction[IO],
    ): IO[Int] =
        for
            hearts <- sql.queryListIO(
                sql"""
            SELECT Hearts.Owner, HeartEssence.Count, Hearts.X, Hearts.Y, Hearts.Z, CivBeacons.World FROM Hearts
                INNER JOIN CivBeacons ON Beacon = ID
                INNER JOIN HeartEssence ON HeartEssence.Owner = Hearts.Owner
                WHERE CivBeacons.GroupID = $uuid
            ORDER BY RANDOM();
            """,
                (uuid *: int4 *: int8 *: int8 *: int8 *: uuid),
                group,
            )
            total <- hearts.foldLeftM(amount) {
                case (total, (heart, essence, x, y, z, world)) =>
                    if total == 0 then IO.pure(0)
                    else
                        val newAmountForThisHeart = (essence - total) max 0
                        val newTotal = (total - essence) max 0
                        if newAmountForThisHeart == essence then IO.pure(total)
                        else
                            for
                                _ <- sql.commandIO(
                                    sql"""
                        UPDATE HeartEssence SET Count = $int4
                            WHERE Owner = $uuid;
                        """,
                                    (newAmountForThisHeart, heart),
                                )
                                _ <- hooks.updateHeart(
                                    Location(
                                        Bukkit.getWorld(world),
                                        x.toDouble,
                                        y.toDouble,
                                        z.toDouble,
                                    ),
                                    newAmountForThisHeart,
                                )
                            yield newTotal
            }
        yield total
