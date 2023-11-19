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
        newOffensiveArea: Polygon,
        defensiveBeacon: BeaconID,
        newDefensiveArea: Polygon,
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
    private val geometryGeojsonCodec = text.imap[Geometry] { json =>
        GeoJsonReader(geometryFactory).read(json).asInstanceOf[Geometry]
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
                    OffensiveBeaconTargetArea GEOMETRY(PolygonZ) NOT NULL,
                    DefensiveBeacon UUID NOT NULL,
                    DefensiveBeaconTargetArea GEOMETRY(PolygonZ) NOT NULL,
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
        sql.queryUniqueIO(
            sql"""
    DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon;
    """,
            (uuid *: uuid),
            battle,
        ).flatMap { (offense, defense) =>
            hooks.battleDefended(battle, offense, defense)
        }
    def defensiveResign(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
    DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, ST_AsGeoJSON(OffensiveBeaconTargetArea), DefensiveBeacon, ST_AsGeoJSON(DefensiveBeaconTargetArea), World;
    """,
            (uuid *: polygonGeojsonCodec *: uuid *: polygonGeojsonCodec *: uuid),
            battle,
        ).flatMap { (offense, area, defense, area2, world) =>
            hooks.battleTaken(battle, offense, area, defense, area2, world)
        }
    def contestedArea(
        battle: BattleID,
        world: UUID,
    )(using Session[IO]): IO[Geometry] =
        sql.queryUniqueIO(
            sql"""
            SELECT ST_AsGeoJSON(ST_Intersection(
                ST_MakeValid((SELECT OffensiveBeaconTargetArea FROM Battles WHERE BattleID = $uuid)),
                ST_MakeValid((SELECT CoveredArea FROM CivBeacons WHERE ID = (SELECT DefensiveBeacon FROM Battles WHERE BattleID = $uuid)))
            ));
            """,
            (geometryGeojsonCodec),
            (battle, battle),
        )
    def startBattle(
        offensive: BeaconID,
        offensiveTargetArea: Polygon,
        defensive: BeaconID,
        defensiveTargetArea: Polygon,
        world: UUID,
    )(using
        Session[IO]
    ): IO[BattleID] =
        sql.queryUniqueIO(
            sql"""
        INSERT INTO Battles (
            BattleID, OffensiveBeacon, OffensiveBeaconTargetArea, DefensiveBeacon, DefensiveBeaconTargetArea, Health, PillarCount, World
        ) SELECT
            gen_random_uuid() as BattleID,
            $uuid as OffensiveBeacon,
            ST_GeomFromGeoJSON($polygonGeojsonCodec) as OffensiveBeaconTargetArea,
            $uuid as DefensiveBeacon,
            ST_GeomFromGeoJSON($polygonGeojsonCodec) as DefensiveBeaconTargetArea,
            $int4 as Health,
            GREATEST(CEILING(ST_Area(ST_Intersection(
                ST_MakeValid(ST_GeomFromGeoJSON($polygonGeojsonCodec)),
                ST_MakeValid((SELECT CoveredArea FROM CivBeacons WHERE ID = $uuid))
            )) / 512.0), 1) as PillarCount,
            $uuid as World
        RETURNING BattleID, PillarCount;
        """,
            (uuid *: int4),
            (
                offensive,
                offensiveTargetArea,
                defensive,
                defensiveTargetArea,
                initialHealth,
                offensiveTargetArea,
                defensive,
                world,
            ),
        ).flatTap { (battleID, count) =>
            contestedArea(battleID, world).flatMap { (polygon) =>
                spawnInitialPillars(
                    battleID,
                    offensive,
                    defensive,
                    polygon,
                    world,
                    count,
                )
            }
        }.map(_._1)
    def pillarDefended(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
    UPDATE Battles SET Health = Health + 1 WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon, World;
    """,
            (uuid *: uuid *: uuid),
            battle,
        ).redeemWith(
            { case SqlState.CheckViolation(_) =>
                sql.queryUniqueIO(
                    sql"""
                    DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon;
                    """,
                    (uuid *: uuid),
                    battle,
                ).flatMap((offense, defense) =>
                    hooks.battleDefended(battle, offense, defense)
                )
            },
            { (offense, defense, world) =>
                contestedArea(battle, world).flatMap { (polygon) =>
                    hooks.spawnPillarFor(
                        battle,
                        offense,
                        polygon,
                        world,
                        defense,
                    )
                }
            },
        ).map(_ => ())
    def pillarTaken(battle: BattleID)(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
    UPDATE Battles SET Health = Health - 1 WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon, World;
    """,
            (uuid *: uuid *: uuid),
            battle,
        ).redeemWith(
            { case SqlState.CheckViolation(_) =>
                sql.queryUniqueIO(
                    sql"""
                    DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, ST_AsGeoJSON(OffensiveBeaconTargetArea), DefensiveBeacon, ST_AsGeoJSON(DefensiveBeaconTargetArea), World;
                    """,
                    (uuid *: polygonGeojsonCodec *: uuid *: polygonGeojsonCodec *: uuid),
                    battle,
                ).flatMap((offense, area, defense, area2, world) =>
                    hooks.battleTaken(
                        battle,
                        offense,
                        area,
                        defense,
                        area2,
                        world,
                    )
                )
            },
            { (offense, defense, world) =>
                contestedArea(battle, world).flatMap { (polygon) =>
                    hooks.spawnPillarFor(
                        battle,
                        offense,
                        polygon,
                        world,
                        defense,
                    )
                }
            },
        ).map(_ => ())
