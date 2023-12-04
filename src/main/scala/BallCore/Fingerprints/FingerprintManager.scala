package BallCore.Fingerprints

import BallCore.Storage.SQLManager
import BallCore.Storage.Migration
import skunk.implicits._
import cats.effect.std.Random
import BallCore.DataStructures.Clock
import java.util.UUID
import skunk.Session
import skunk.Transaction
import cats.data.OptionT
import skunk.codec.all._
import cats.effect.IO
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.locationtech.jts.io.geojson.GeoJsonWriter
import org.locationtech.jts.geom.Coordinate
import java.time.OffsetDateTime
import cats.effect.std.UUIDGen

case class Fingerprint(
    creator: String,
    createdAt: OffsetDateTime,
    reason: FingerprintReason,
)

object FingerprintReason:
    def fromLabel(s: String): Option[FingerprintReason] =
        values.find(_.label == s)

enum FingerprintReason(val label: String):
    case bustedThrough extends FingerprintReason("busted_through")

    def explain: String =
        this match
            case bustedThrough => "Busted through a beacon"

val reasonEnum = text.eimap[FingerprintReason](
    FingerprintReason.fromLabel(_).toRight("not found")
)(_.label)

class FingerprintManager()(using
    sql: SQLManager,
    random: Random[IO],
    clock: Clock,
):
    val _ = clock
    val geometryFactory = GeometryFactory()

    private val pointGeojsonCodec = text.imap[Point] { json =>
        GeoJsonReader(geometryFactory).read(json).asInstanceOf[Point]
    } { polygon =>
        GeoJsonWriter().write(polygon)
    }

    sql.applyMigration(
        Migration(
            "Initial Fingerprint Manager",
            List(
                sql"""
                CREATE TABLE PlayerFingerprints (
                    Fingerprint TEXT PRIMARY KEY,
                    PlayerID UUID NOT NULL UNIQUE
                );
                """.command,
                sql"""
                CREATE TABLE Fingerprints (
                    ID UUID PRIMARY KEY,
                    Location GEOMETRY (PointZ) NOT NULL,
                    World UUID NOT NULL,
                    Fingerprint TEXT NOT NULL REFERENCES PlayerFingerprints(Fingerprint),
                    Reason TEXT NOT NULL,
                    CreatedAt TIMESTAMPTZ NOT NULL
                );
                """.command,
                sql"""
                CREATE INDEX fingerprints_location ON fingerprints USING SPGIST (location);
                """.command,
            ),
            List(
                sql"""
                DROP INDEX fingerprints_location;
                """.command,
                sql"""
                DROP TABLE Fingerprints;
                """.command,
                sql"""
                DROP TABLE PlayerFingerprints;
                """.command,
            ),
        )
    )

    def fingerprintFor(
        player: UUID
    )(using Session[IO], Transaction[IO]): IO[String] =
        OptionT(
            sql.queryOptionIO(
                sql"""
        SELECT Fingerprint FROM PlayerFingerprints WHERE PlayerID = $uuid
        """,
                text,
                player,
            )
        ).getOrElseF(for {
            newID <- random.nextAlphaNumeric
                .replicateA(6)
                .map(_.mkString.toUpperCase())
            _ <- sql.commandIO(
                sql"""
            INSERT INTO PlayerFingerprints (
                Fingerprint, PlayerID
            ) VALUES (
                $text, $uuid
            )
            """,
                (newID, player),
            )
        } yield newID)

    def storeFingerprintAt(
        x: Int,
        y: Int,
        z: Int,
        world: UUID,
        player: UUID,
        reason: FingerprintReason,
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for {
            id <- UUIDGen[IO].randomUUID
            fingerprint <- fingerprintFor(player)
            now <- clock.nowIO()
            point = geometryFactory.createPoint(Coordinate(x, z, y))
            _ <- sql.commandIO(
                sql"""
            INSERT INTO Fingerprints (
                ID, Location, World, Fingerprint, CreatedAt, Reason
            ) VALUES (
                $uuid, ST_GeomFromGeoJSON($pointGeojsonCodec), $uuid, $text, $timestamptz, $reasonEnum
            )
            """,
                (id, point, world, fingerprint, now, reason),
            )
        } yield ()

    def fingerprintsInTheVicinityOf(
        x: Int,
        y: Int,
        z: Int,
        world: UUID,
    )(using Session[IO]): IO[List[Fingerprint]] =
        val point = geometryFactory.createPoint(Coordinate(x, z, y))
        sql.queryListIO(
            sql"""
            DELETE FROM Fingerprints WHERE
                World = $uuid
                AND ST_3DDistance(Location, ST_GeomFromGeoJSON($pointGeojsonCodec)) < 3.0
                RETURNING Fingerprint, CreatedAt, Reason;
            """,
            (text *: timestamptz *: reasonEnum).to[Fingerprint],
            (world, point),
        )
