package CivCubed.Rest

import CivCubed.Storage.{Migration, SQLManager}
import cats.effect.IO
import skunk.Session
import skunk.codec.all.*
import skunk.postgis.codecs.all.*
import skunk.postgis.Point
import skunk.implicits.*
import java.util.UUID
import CivCubed.DataStructures.Clock
import cats.data.OptionT
import scala.collection.concurrent.TrieMap
import org.bukkit.Location
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.math.Ordering.Implicits._
import cats.effect.kernel.Resource

trait RestManagerHooks:
    def updateSidebar(playerID: UUID, rest: Double): IO[Unit]
    def evaluateRoomAt(rm: RestManager, location: Location)(using Session[IO]): IO[Option[(RoomRole, Evaluation, Bounds)]]

val location = point.eimap { point =>
    point.coordinate.z match
        case None => Left("missing point")
        case Some(value) =>
            Right((point.coordinate.x, point.coordinate.y, value))
} { (x, y, z) =>
    Point.xyz(x, y, z)
}

val locationi = location.imap { (x, y, z) =>
    (x.toInt, y.toInt, z.toInt)
} { (x, y, z) =>
    (x.toDouble, y.toDouble, z.toDouble)
}

// val restrole = `enum`[RoomRole](_.toString, it => RoomRole.values.find(_.toString == it), Type("RestRole"))
// val resttier = `enum`[Evaluation](_.toString, it => Evaluation.values.find(_.toString == it), Type("RestTier"))
val restrole = text.eimap[RoomRole](it => RoomRole.values.find(_.toString == it).toRight(s"unknown role value ${it}"))(_.toString)
val resttier = text.eimap[Evaluation](it => Evaluation.values.find(_.toString == it).toRight(s"unknown tier value ${it}"))(_.toString)

/** Manages rest related tasks and utilities.
  */
class RestManager()(using sql: SQLManager, c: Clock, hooks: RestManagerHooks):
    sql.applyMigration(
        Migration(
            "Initial Rest Manager",
            List(
                sql"""
                    CREATE TYPE RestTier AS ENUM (
                        'luxurious',
                        'good',
                        'modest',
                        'nothing'
                    );
                    """.command,
                sql"""
                    CREATE TYPE RestRole AS ENUM (
                        'mining',
                        'animals',
                        'crops',
                        'industrialist',
                        'enchantments',
                        'travel',
                        'builder',
                        'soldier',
                        'nothing'
                    );
                    """.command,
                sql"""
                    CREATE TABLE RestValues (
                        PlayerID UUID PRIMARY KEY,
                        LoggedOffAt TIMESTAMPTZ NOT NULL,
                        LoggedOffTier TEXT NOT NULL,
                        LoggedOffRole TEXT NOT NULL,
                        Tier TEXT NOT NULL,
                        Role TEXT NOT NULL,
                        SpawnLocation GEOMETRY(PointZ),
                        SpawnLocationWorld UUID,
                        Rest DOUBLE PRECISION NOT NULL DEFAULT 1.0,
                        CHECK(Rest BETWEEN 0.0 AND 1.0),
                        CHECK(
                            (SpawnLocation IS NOT NULL AND SpawnLocationWorld IS NOT NULL)
                            OR
                            (SpawnLocation IS NULL AND SpawnLocationWorld IS NULL)
                        )
                    );
                    """.command,
                sql"""
                    CREATE INDEX RestValuesGeom ON RestValues USING GIST (SpawnLocation);
                    """.command,
            ),
            List(
                sql"""DROP TABLE RestValues;""".command
            ),
        )
    )

    private val restConsumptionAmount = 1.0 / 4000.0
    private val cache = TrieMap[UUID, (RoomRole, Evaluation, Double)]()
    private def getOrElseUpdateF(
        key: UUID
    )(default: => IO[(RoomRole, Evaluation, Double)]): IO[(RoomRole, Evaluation, Double)] =
        OptionT(IO { cache.get(key) }).getOrElseF {
            for {
                value <- default
                _ <- IO { cache.update(key, value) }
            } yield value
        }
    private def updateF(key: UUID, value: (RoomRole, Evaluation, Double)): IO[Unit] =
        IO {
            cache.update(key, value)
        }
    private def removeF(key: UUID): IO[Unit] =
        IO {
            val _ = cache.remove(key)
        }

    def setSpawnLocation(playerID: UUID, location: (Int, Int, Int), world: UUID)(using Session[IO]): IO[Unit] =
        sql.commandIO(
            sql"""
            UPDATE RestValues SET SpawnLocation = $locationi, SpawnLocationWorld = $uuid WHERE PlayerID = $uuid;
            """,
            (location, world, playerID),
        ).map(_ => ())

    def getSpawnLocationsWithin(min: (Int, Int, Int), max: (Int, Int, Int), world: UUID)(using Session[IO]): IO[List[UUID]] =
        sql.queryListIO(
            sql"""
            SELECT PlayerID FROM RestValues WHERE ST_3DIntersects(SpawnLocation, ST_3DMakeBox($locationi, $locationi)) AND SpawnLocationWorld = $uuid;
            """,
            (uuid),
            (min, max, world),
        )

    def getSpecialization(playerID: UUID)(using rsrc: Resource[IO, Session[IO]]): IO[(RoomRole, Evaluation)] =
        getOrElseUpdateF(playerID) {
            rsrc.use { implicit session =>
                OptionT(sql.queryOptionIO(sql"SELECT Role, Tier, Rest FROM RestValues WHERE PlayerId = $uuid", (restrole *: resttier *: float8), playerID))
                    .getOrElse((RoomRole.nothing, Evaluation.nothing, 1.0))
            }
        }.map(it => (it._1, it._2))
    /** Consumes rest, returning if the rest bonus can be applied
      */
    def useRest(playerID: UUID)(using Session[IO]): IO[Boolean] =
        for {
            currentRest <- getOrElseUpdateF(playerID) {
                OptionT(sql.queryOptionIO(sql"SELECT Role, Tier, Rest FROM RestValues WHERE PlayerId = $uuid", (restrole *: resttier *: float8), playerID))
                    .getOrElse((RoomRole.nothing, Evaluation.nothing, 1.0))
            }
            newRestRaw = currentRest._3 - restConsumptionAmount
            newRest = newRestRaw max 0.0
            _ <- updateF(playerID, (currentRest._1, currentRest._2, newRest))
            _ <- sql
                .commandIO(
                    sql"""
                    UPDATE RestValues SET Rest = $float8 WHERE PlayerID = $uuid;
                    """,
                    (newRest, playerID),
                )
            _ <- hooks.updateSidebar(playerID, newRest)
        } yield newRestRaw >= 0

    /** Marks when a player has logged off
      *
      * @param playerId
      *   The player's Minecraft UUID.
      */
    def logoff(playerID: UUID, location: Location)(using Session[IO]): IO[Unit] =
        for {
            currentTime <- c.nowIO()
            evaluation <- hooks.evaluateRoomAt(this, location)
            (role, tier, _) = evaluation.getOrElse((RoomRole.nothing, Evaluation.nothing, null))
            _ <- sql.commandIO(
                sql"""
                UPDATE RestValues SET LoggedOffAt = $timestamptz, LoggedOffRole = $restrole, LoggedOffTier = $resttier WHERE PlayerID = $uuid;
                """,
                (currentTime, role, tier, playerID),
            )
            _ <- removeF(playerID)
        } yield ()

    def evaluateRestInRestZone(
        playerID: UUID,
        logOnLocation: Location,
        rest: Double,
        logOffTime: OffsetDateTime,
        role: RoomRole,
        tier: Evaluation,
        logOffRole: RoomRole,
        logOffTier: Evaluation,
        roomData: (RoomRole, Evaluation, Bounds)
    )(using Session[IO]): IO[(Double, RoomRole, Evaluation)] =
        for
            currentTime <- c.nowIO()
            difference = ChronoUnit.HOURS.between(logOffTime, currentTime)
            (logOnRole, logOnTier, _) = roomData

            restGained = difference.toDouble / (12.0)
            newRest = (rest + restGained) min 1.0
        yield
            val actualTier =
                if logOnRole == RoomRole.soldier then
                    logOnTier
                else
                    logOnTier.min(logOffTier)
            val actualRole = logOnRole

            if logOffRole != logOnRole then
                (rest, RoomRole.nothing, Evaluation.nothing)
            else if ((role, tier) == (actualRole, actualTier)) then
                (newRest, actualRole, actualTier)
            else if difference >= 8.0 && role == actualRole then
                if actualTier > tier then
                    (newRest, actualRole, tier.next.min(actualTier))
                else
                    (newRest, actualRole, actualTier)
            else if difference >= 8.0 then
                (newRest, actualRole, Evaluation.nothing)
            else
                (rest, role, tier)

    /** Marks when a player has logged on
      */
    def logon(playerID: UUID, logOnLocation: Location)(using Session[IO]): IO[Unit] =
        for {
            data <- OptionT(
                sql.queryOptionIO(
                    sql"""
                    SELECT Rest, LoggedOffAt, Tier, Role, LoggedOffTier, LoggedOffRole, SpawnLocation, SpawnLocationWorld FROM RestValues WHERE PlayerID = $uuid
                    """,
                    (float8 *: timestamptz *: resttier *: restrole *: resttier *: restrole *: locationi.opt *: uuid.opt),
                    playerID,
                )
            ).getOrElseF {
                for
                    time <- c.nowIO()
                yield (1.0, time, Evaluation.nothing, RoomRole.nothing, Evaluation.nothing, RoomRole.nothing, None, None)
            }
            (rest, logOffTime, tier, role, logOffTier, logOffRole, spawnLocation, spawnLocationWorld) = data
            sameWorld = spawnLocationWorld.exists(_ == logOnLocation.getWorld.getUID)
            bounds <-
                IO.pure(sameWorld)
                    .ifM(hooks.evaluateRoomAt(this, logOnLocation), IO.pure(None))
            spawnLocationIsWithinBoundsOfLogonRoom = bounds.zip(spawnLocation).map { (bounds, location) =>
                bounds._3.contains(location)
            }.getOrElse(false)

            currentTime <- c.nowIO()
            newData <- IO.pure(spawnLocationIsWithinBoundsOfLogonRoom)
                .ifM(
                    evaluateRestInRestZone(playerID, logOnLocation, rest, logOffTime, role, tier, logOffRole, logOffTier, bounds.get),
                    IO.pure(rest, role, tier),
                )

            _ <- sql.commandIO(
                sql"""
                INSERT INTO RestValues (
                    PlayerID, LoggedOffAt, Tier, Role, LoggedOffTier, LoggedOffRole, SpawnLocation, SpawnLocationWorld, Rest
                ) VALUES (
                    $uuid, $timestamptz, $resttier, $restrole, $resttier, $restrole, ${locationi.opt}, ${uuid.opt}, $float8
                ) ON CONFLICT (PlayerID) DO UPDATE SET
                    Rest = EXCLUDED.Rest,
                    Tier = EXCLUDED.Tier,
                    Role = EXCLUDED.Role;
            """,
                (playerID, currentTime, newData._3, newData._2, logOffTier, logOffRole, spawnLocation, spawnLocationWorld, newData._1),
            )
        } yield ()
