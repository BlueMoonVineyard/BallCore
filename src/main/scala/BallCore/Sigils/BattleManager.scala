package BallCore.Sigils

import BallCore.Storage
import skunk.implicits._
import java.util.UUID
import skunk.Session
import cats.effect.IO
import skunk.codec.all._
import skunk.SqlState
import BallCore.Beacons.BeaconID
import BallCore.Beacons.CivBeaconManager
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.locationtech.jts.io.geojson.GeoJsonWriter
import org.locationtech.jts.geom.Geometry
import scala.collection.concurrent.TrieMap
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.audience.Audience
import java.{util => ju}
import BallCore.PrimeTime.PrimeTimeManager
import cats.data.OptionT
import skunk.Transaction
import java.time.OffsetDateTime
import BallCore.PrimeTime.PrimeTimeResult
import BallCore.DataStructures.Clock
import java.time.temporal.ChronoUnit
import cats.syntax.all._
import cats.effect.Resource
import cats.effect.Fiber
import scala.util.NotGiven
import com.github.plokhotnyuk.rtree2d.core.RTree
import com.github.plokhotnyuk.rtree2d.core.RTreeEntry
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
import org.locationtech.jts.geom.GeometryCollection
import org.bukkit.Location
import org.locationtech.jts.geom.Coordinate

type BattleID = UUID

enum BattleError:
    case beaconIsTooNew
    case opponentIsInPrimeTime(opensAt: OffsetDateTime)

trait BattleHooks:
    def spawnPillarFor(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        contestedArea: Geometry,
        world: UUID,
        defensiveBeacon: BeaconID,
        pillarWasDefended: Option[Boolean],
    )(using Session[IO], Transaction[IO]): IO[Unit]
    def battleDefended(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
    )(using Session[IO], Transaction[IO]): IO[Unit]
    def battleTaken(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
        areaToRemoveFromDefense: Polygon,
        claimToSetOffenseTo: Polygon,
        world: UUID,
    )(using Session[IO], Transaction[IO]): IO[Unit]
    def impendingBattle(
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
        contestedArea: Geometry,
        world: UUID,
    )(using Session[IO], Transaction[IO]): IO[Unit]

class BattleManager(using
    sql: Storage.SQLManager,
    cbm: CivBeaconManager,
    hooks: BattleHooks,
    primeTime: PrimeTimeManager,
    clock: Clock,
):
    val initialHealth = 6
    val _ = cbm

    private val geometryFactory = GeometryFactory()
    private val polygonGeojsonCodec = text.imap[Polygon] { json =>
        GeoJsonReader(geometryFactory).read(json).asInstanceOf[Polygon]
    } { polygon =>
        GeoJsonWriter().write(polygon)
    }
    private val bossBars = TrieMap[BattleID, (BossBar, List[Audience])]()
    val bufferSize = 10

    private def updateBossBarFor(battle: BattleID, health: Int): IO[BossBar] =
        IO {
            bossBars
                .updateWith(battle) { bar =>
                    import BallCore.TextComponents._
                    bar match
                        case None =>
                            Some(
                                (
                                    BossBar.bossBar(
                                        txt"Battle",
                                        health.toFloat / 10.0f,
                                        BossBar.Color.RED,
                                        BossBar.Overlay.NOTCHED_10,
                                        ju.Set.of(
                                            BossBar.Flag.DARKEN_SCREEN,
                                            BossBar.Flag.CREATE_WORLD_FOG,
                                            BossBar.Flag.PLAY_BOSS_MUSIC,
                                        ),
                                    ),
                                    List(),
                                )
                            )
                        case Some(bar, audience) =>
                            Some(
                                (bar.progress(health.toFloat / 10.0f), audience)
                            )
                }
                .get
                ._1
        }
    private def removeBossBarFor(battle: BattleID): IO[Unit] =
        IO {
            bossBars.remove(battle) match
                case None =>
                case Some((bar, audience)) =>
                    audience.foreach(bar.removeViewer)
        }
    def showBossBarTo(battle: BattleID, target: Audience): IO[Unit] =
        IO {
            val _ = bossBars.updateWith(battle) {
                case Some((bar, audience)) if !audience.contains(target) =>
                    bar.addViewer(target)
                    Some((bar, target :: audience))
                case x => x
            }
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
                    DesiredArea GEOMETRY(PolygonZ) NOT NULL,
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

    private case class WorldData(
        var battleRTree: RTree[(Polygon, BattleID)],
        var battleIDsToRTreeEntries: Map[BeaconID, IndexedSeq[
            RTreeEntry[(Polygon, BattleID)]
        ]],
    )
    private def triangulate(
        id: BattleID,
        polygon: Polygon,
    ): IndexedSeq[RTreeEntry[(Polygon, BattleID)]] =
        import com.github.plokhotnyuk.rtree2d.core.*
        import com.github.plokhotnyuk.rtree2d.core.EuclideanPlane.*

        val triangulator = DelaunayTriangulationBuilder()
        triangulator.setSites(polygon)
        val triangleCollection = triangulator
            .getTriangles(geometryFactory)
            .asInstanceOf[GeometryCollection]
        val triangles =
            for i <- 0 until triangleCollection.getNumGeometries
            yield triangleCollection.getGeometryN(i).asInstanceOf[Polygon]

        triangles.map { triangle =>
            val Array(minmin, _, maxmax, _, _) =
                triangle.getEnvelope
                    .asInstanceOf[Polygon]
                    .getCoordinates

            entry(
                minmin.getX.toFloat,
                minmin.getY.toFloat,
                maxmax.getX.toFloat,
                maxmax.getY.toFloat,
                (triangle, id),
            )
        }
    private def loadBattles(
        world: UUID
    )(using Session[IO]): IO[RTree[(Polygon, BattleID)]] =
        sql
            .queryListIO(
                sql"""
        SELECT
            BattleID, ST_AsGeoJSON(ContestedArea)
        FROM
            Battles
        WHERE
            World = $uuid;
        """,
                uuid *: polygonGeojsonCodec.opt,
                world,
            )
            .map(
                _.flatMap((it, bytes) =>
                    bytes.map(x =>
                        (it, x.buffer(bufferSize).asInstanceOf[Polygon])
                    )
                )
                    .flatMap(triangulate)
            )
            .map(RTree(_))
    private val worldData = TrieMap[UUID, WorldData]()
    private def getWorldData(
        world: UUID
    )(using rsrc: Resource[IO, Session[IO]]): IO[WorldData] =
        if !worldData.contains(world) then
            for {
                rtree <- rsrc.use { implicit session => loadBattles(world) }
            } yield {
                val entries = rtree.entries.groupMap(_.value._2)(identity)
                worldData += world -> WorldData(rtree, entries)
                worldData(world)
            }
        else IO.pure(worldData(world))

    private def spawnInitialPillars(
        battle: BattleID,
        offense: BeaconID,
        defense: BeaconID,
        contestedArea: Geometry,
        world: UUID,
        count: Int,
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        (1 to count).toList
            .traverse(_ =>
                hooks.spawnPillarFor(
                    battle,
                    offense,
                    contestedArea,
                    world,
                    defense,
                    None,
                )
            )
            .map(_ => ())

    def offensiveResign(
        battle: BattleID
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        battleDefended(battle)
    def defensiveResign(
        battle: BattleID
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        battleTaken(battle)

    def bufferZoneAt(l: Location)(using
        Resource[IO, Session[IO]]
    ): IO[Option[BattleID]] =
        val lx = l.getX.toFloat
        val ly = l.getZ.toFloat
        getWorldData(l.getWorld.getUID).map { worldData =>
            worldData.battleRTree
                .searchAll(lx, ly)
                .filter { entry =>
                    val (polygon, _) = entry.value
                    polygon.covers(
                        geometryFactory.createPoint(Coordinate(l.getX, l.getZ))
                    )
                }
                .map(_.value._2)
                .headOption
        }

    def isInvolvedInBattle(beacon: BeaconID)(using Session[IO]): IO[Boolean] =
        sql.queryUniqueIO(
            sql"""
        SELECT EXISTS(SELECT 1 FROM Battles WHERE OffensiveBeacon = $uuid OR DefensiveBeacon = $uuid);
        """,
            bool,
            (beacon, beacon),
        )
    // offensive then defensive
    def battlesThatBeaconIsInvolvedIn(
        beacon: BeaconID
    )(using Session[IO]): IO[(List[BattleID], List[BattleID])] =
        for {
            a <- sql.queryListIO(
                sql"""
            SELECT BattleID FROM Battles WHERE OffensiveBeacon = $uuid;
            """,
                uuid,
                (beacon),
            )
            b <- sql.queryListIO(
                sql"""
            SELECT BattleID FROM Battles WHERE DefensiveBeacon = $uuid;
            """,
                uuid,
                (beacon),
            )
        } yield (a, b)
    private def spawnBattleFiber(
        offensive: BeaconID,
        defensive: BeaconID,
        contestedArea: Polygon,
        desiredArea: Polygon,
        world: UUID,
    )(using r: Resource[IO, Session[IO]]): IO[Fiber[IO, Throwable, BattleID]] =
        (r.use { implicit session =>
            sql.withTX(for {
                _ <- hooks
                    .impendingBattle(offensive, defensive, contestedArea, world)
                result <- sql.queryUniqueIO(
                    sql"""
                INSERT INTO Battles (
                    BattleID, OffensiveBeacon, DefensiveBeacon, ContestedArea, DesiredArea, Health, PillarCount, World
                ) SELECT
                    gen_random_uuid() as BattleID,
                    $uuid as OffensiveBeacon,
                    $uuid as DefensiveBeacon,
                    ST_GeomFromGeoJSON($polygonGeojsonCodec) as ContestedArea,
                    ST_GeomFromGeoJSON($polygonGeojsonCodec) as DesiredArea,
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
                        desiredArea,
                        initialHealth,
                        contestedArea,
                        world,
                    ),
                )
                // TODO: multiple slime pillars
                (battleID, count) = result
                worldData <- getWorldData(world)
                _ <- IO {
                    val triangulated = triangulate(
                        battleID,
                        contestedArea.buffer(bufferSize).asInstanceOf[Polygon],
                    )
                    worldData.battleRTree = RTree.update(
                        worldData.battleRTree,
                        Seq(),
                        triangulated,
                    )
                    worldData.battleIDsToRTreeEntries += battleID -> triangulated
                }
                _ <- spawnInitialPillars(
                    battleID,
                    offensive,
                    defensive,
                    contestedArea,
                    world,
                    1,
                )
                _ <- updateBossBarFor(battleID, initialHealth)
            } yield battleID)
        }).start
    def startBattle(
        offensive: BeaconID,
        defensive: BeaconID,
        contestedArea: Polygon,
        desiredArea: Polygon,
        world: UUID,
    )(using
        Session[IO],
        Transaction[IO],
        Resource[IO, Session[IO]],
    ): IO[Either[BattleError, Fiber[IO, Throwable, BattleID]]] =
        for {
            now <- clock.nowIO()
            offensiveCreatedAt <- OptionT(cbm.beaconCreatedAt(offensive))
                .getOrElseF(clock.nowIO())
            offensiveAge = ChronoUnit.DAYS.between(offensiveCreatedAt, now)
            primeTime <- OptionT(cbm.getGroup(defensive))
                .flatMap { group =>
                    OptionT.liftF(primeTime.checkPrimeTime(group))
                }
                .getOrElse(PrimeTimeResult.isInPrimeTime)
            result <- primeTime match
                case PrimeTimeResult.isInPrimeTime if offensiveAge > 3 =>
                    spawnBattleFiber(
                        offensive,
                        defensive,
                        contestedArea,
                        desiredArea,
                        world,
                    ).map(Right.apply)
                case PrimeTimeResult.isInPrimeTime =>
                    IO.pure(Left(BattleError.beaconIsTooNew))
                case PrimeTimeResult.notInPrimeTime(_) if offensiveAge <= 3 =>
                    IO.pure(Left(BattleError.beaconIsTooNew))
                case PrimeTimeResult.notInPrimeTime(reopens) =>
                    IO.pure(Left(BattleError.opponentIsInPrimeTime(reopens)))
        } yield result
    private def removeFromBattleMap(battleID: BattleID, world: UUID)(using
        s: Session[IO]
    ): IO[Unit] =
        for {
            worldData <- getWorldData(world)(using
                Resource.make(IO.pure(s))(_ => IO.unit)
            )
            _ <- IO {
                worldData.battleRTree = RTree.update(
                    worldData.battleRTree,
                    worldData.battleIDsToRTreeEntries
                        .get(battleID)
                        .toSeq
                        .flatten,
                    None,
                )
            }
        } yield ()
    private def battleDefended(
        battle: BattleID
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
            DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon, World;
            """,
            (uuid *: uuid *: uuid),
            battle,
        ).flatTap { _ =>
            removeBossBarFor(battle)
        }.flatTap { case (_, _, world) =>
            removeFromBattleMap(battle, world)
        }.flatMap((offense, defense, world) =>
            hooks.battleDefended(battle, offense, defense)
        )
    def pillarDefended(
        battle: BattleID
    )(using Session[IO], NotGiven[Transaction[IO]]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
    UPDATE Battles SET Health = Health + 1 WHERE BattleID = $uuid RETURNING Health, OffensiveBeacon, DefensiveBeacon, ST_AsGeoJSON(ContestedArea), World;
    """,
            (int4 *: uuid *: uuid *: polygonGeojsonCodec *: uuid),
            battle,
        ).redeemWith(
            { case SqlState.CheckViolation(_) =>
                sql.withTX(battleDefended(battle))
            },
            { (health, offense, defense, contestedArea, world) =>
                sql.withTX(
                    hooks
                        .spawnPillarFor(
                            battle,
                            offense,
                            contestedArea,
                            world,
                            defense,
                            Some(true),
                        )
                        .flatTap(_ => updateBossBarFor(battle, health))
                )
            },
        ).map(_ => ())
    private def battleTaken(
        battle: BattleID
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
            DELETE FROM Battles WHERE BattleID = $uuid RETURNING OffensiveBeacon, DefensiveBeacon, ST_AsGeoJSON(ContestedArea), ST_AsGeoJSON(DesiredArea), World;
            """,
            (uuid *: uuid *: polygonGeojsonCodec *: polygonGeojsonCodec *: uuid),
            battle,
        ).flatTap { _ =>
            removeBossBarFor(battle)
        }.flatTap { case (_, _, _, _, world) =>
            removeFromBattleMap(battle, world)
        }.flatMap((offense, defense, contestedArea, desiredArea, world) =>
            hooks.battleTaken(
                battle,
                offense,
                defense,
                contestedArea,
                desiredArea,
                world,
            )
        )

    def pillarTaken(
        battle: BattleID
    )(using Session[IO], NotGiven[Transaction[IO]]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
    UPDATE Battles SET Health = Health - 1 WHERE BattleID = $uuid RETURNING Health, OffensiveBeacon, DefensiveBeacon, ST_AsGeoJSON(ContestedArea), World;
    """,
            (int4 *: uuid *: uuid *: polygonGeojsonCodec *: uuid),
            battle,
        ).redeemWith(
            { case SqlState.CheckViolation(_) =>
                sql.withTX(battleTaken(battle))
            },
            { (health, offense, defense, contestedArea, world) =>
                sql.withTX(
                    hooks
                        .spawnPillarFor(
                            battle,
                            offense,
                            contestedArea,
                            world,
                            defense,
                            Some(false),
                        )
                        .flatTap(_ => updateBossBarFor(battle, health))
                )
            },
        ).map(_ => ())
