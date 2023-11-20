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
import BallCore.Beacons.CivBeaconManager
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.locationtech.jts.io.geojson.GeoJsonWriter
import org.locationtech.jts.geom.Geometry

type BattleID = UUID

trait BattleHooks:
    def spawnPillarFor(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        contestedArea: Geometry,
        world: UUID,
        defensiveBeacon: BeaconID,
    )(using Session[IO]): IO[Unit]
    def battleDefended(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
    )(using Session[IO]): IO[Unit]
    def battleTaken(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
        contestedArea: Polygon,
        world: UUID,
    )(using Session[IO]): IO[Unit]

class BattleManager(using
    sql: Storage.SQLManager,
    cbm: CivBeaconManager,
    hooks: BattleHooks,
):
    val initialHealth = 6
    val _ = cbm

    private val geometryFactory = GeometryFactory()
    private val polygonGeojsonCodec = text.imap[Polygon] { json =>
        GeoJsonReader(geometryFactory).read(json).asInstanceOf[Polygon]
    } { polygon =>
        GeoJsonWriter().write(polygon)
    }

    sql.applyMigration(
        Storage.Migration(
            "Initial Battle Manager",
            List(
                sql"""
                CREATE TABLE Battles(
                    BattleID UUID PRIMARY KEY,
                    OffensiveBeacon UUID NOT NULL,
                    DefensiveBeacon UUID NOT NULL,
                    ContestedArea GEOMETRY(PolygonZ) NOT NULL,
                    Health INTEGER NOT NULL,
                    PillarCount INTEGER NOT NULL,
                    World UUID NOT NULL,
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

    private def spawnInitialPillars(
        battle: BattleID,
        offense: BeaconID,
        defense: BeaconID,
        contestedArea: Geometry,
        world: UUID,
        count: Int,
    )(using Session[IO]): IO[Unit] =
        (1 to count).toList
            .traverse(_ =>
                hooks.spawnPillarFor(
                    battle,
                    offense,
                    contestedArea,
                    world,
                    defense,
                )
            )
            .map(_ => ())

    def offensiveResign(battle: BattleID)(using Session[IO]): IO[Unit] =
        battleDefended(battle)
    def defensiveResign(battle: BattleID)(using Session[IO]): IO[Unit] =
        battleTaken(battle)

    def startBattle(
        offensive: BeaconID,
        defensive: BeaconID,
        contestedArea: Polygon,
        world: UUID,
    )(using
        Session[IO]
    ): IO[BattleID] =
        sql.queryUniqueIO(
            sql"""
        INSERT INTO Battles (
            BattleID, OffensiveBeacon, DefensiveBeacon, ContestedArea, Health, PillarCount, World
        ) SELECT
            gen_random_uuid() as BattleID,
            $uuid as OffensiveBeacon,
            $uuid as DefensiveBeacon,
            ST_GeomFromGeoJSON($polygonGeojsonCodec) as ContestedArea,
            $int4 as Health,
            GREATEST(CEILING(ST_Area(ST_MakeValid(ST_GeomFromGeoJSON($polygonGeojsonCodec))) / 512.0), 1) as PillarCount,
            $uuid as World
        RETURNING BattleID, PillarCount;
        """,
            (uuid *: int4),
            (
                offensive,
                defensive,
                contestedArea,
                initialHealth,
                contestedArea,
                world,
            ),
        ).flatTap { (battleID, count) =>
            spawnInitialPillars(
                battleID,
                offensive,
                defensive,
                contestedArea,
                world,
                count,
            )
        }.map(_._1)
    private def battleDefended(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
            DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon;
            """,
            (uuid *: uuid),
            battle,
        ).flatMap((offense, defense) =>
            hooks.battleDefended(battle, offense, defense)
        )
    def pillarDefended(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
    UPDATE Battles SET Health = Health + 1 WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon, ST_AsGeoJSON(ContestedArea), World;
    """,
            (uuid *: uuid *: polygonGeojsonCodec *: uuid),
            battle,
        ).redeemWith(
            { case SqlState.CheckViolation(_) =>
                battleDefended(battle)
            },
            { (offense, defense, contestedArea, world) =>
                hooks.spawnPillarFor(
                    battle,
                    offense,
                    contestedArea,
                    world,
                    defense,
                )
            },
        ).map(_ => ())
    private def battleTaken(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
            DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon, ST_AsGeoJSON(ContestedArea), World;
            """,
            (uuid *: uuid *: polygonGeojsonCodec *: uuid),
            battle,
        ).flatMap((offense, defense, contestedArea, world) =>
            hooks.battleTaken(
                battle,
                offense,
                defense,
                contestedArea,
                world,
            )
        )

    def pillarTaken(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
    UPDATE Battles SET Health = Health - 1 WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon, ST_AsGeoJSON(ContestedArea), World;
    """,
            (uuid *: uuid *: polygonGeojsonCodec *: uuid),
            battle,
        ).redeemWith(
            { case SqlState.CheckViolation(_) =>
                battleTaken(battle)
            },
            { (offense, defense, contestedArea, world) =>
                hooks.spawnPillarFor(
                    battle,
                    offense,
                    contestedArea,
                    world,
                    defense,
                )
            },
        ).map(_ => ())
