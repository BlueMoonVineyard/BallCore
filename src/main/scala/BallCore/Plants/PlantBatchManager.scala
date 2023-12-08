// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Plants

import BallCore.DataStructures.{Actor, Clock}
import BallCore.Datekeeping.Datekeeping.Periods
import BallCore.Datekeeping.{DateUnit, Datekeeping, Month}
import BallCore.Folia.{
    ChunkExecutionContext,
    FireAndForget,
    LocationExecutionContext,
}
import BallCore.Storage
import BallCore.Storage.SQLManager
import BallCore.UI.ChatElements.*
import cats.effect.*
import cats.syntax.all.*
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import skunk.Session
import skunk.codec.all.*
import skunk.implicits.*

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.chaining.*
import io.sentry.Sentry
import io.sentry.SpanStatus
import net.kyori.adventure.bossbar.BossBar
import scala.collection.concurrent.TrieMap
import org.bukkit.World
import org.bukkit.Chunk

enum PlantMsg:
    case startGrowing(what: Plant, where: Block)
    case stopGrowing(where: Block)
    case tickPlants
    case inspect(where: Block, player: Player)
    case chunkLoaded(chunk: Chunk)

case class DBPlantData(
    chunkX: Int,
    chunkZ: Int,
    world: UUID,
    offsetX: Int,
    offsetZ: Int,
    yPos: Int,
    what: Plant,
    ageIngameHours: Int,
    incompleteGrowthAdvancements: Int,
    isInValidClimate: Boolean,
)

case class Dirty[A](
    inner: A,
    dirty: Boolean,
    deleted: Boolean,
)

val plantKindCodec = text.imap { str => Plant.valueOf(str) } { it =>
    it.toString
}

class PlantBatchManager()(using sql: SQLManager, p: Plugin, c: Clock)
    extends Actor[PlantMsg]:
    private def millisToNextHour(): Long =
        val nextHour =
            LocalDateTime
                .now()
                .plus(1, DateUnit.hour)
                .truncatedTo(DateUnit.hour)
        LocalDateTime.now().until(nextHour, ChronoUnit.MILLIS)

    protected def handleInit(): Unit =
        p.getServer.getAsyncScheduler
            .runAtFixedRate(
                p,
                _ => send(PlantMsg.tickPlants),
                millisToNextHour(),
                Periods.hour.toMillis,
                TimeUnit.MILLISECONDS,
            )
        sql.useBlocking(sql.withS(load()))
        val count = plants.foldLeft(0)((sum, map) => map._2.size + sum)
        println(s"Loaded ${count} plants")

    protected def handleShutdown(): Unit =
        val count = plants.foldLeft(0)((sum, map) => map._2.size + sum)
        println(s"Saving ${count} plants")
        sql.useBlocking(sql.withS(saveAll()))
        println(s"Saved ${count} plants!")

    sql.applyMigration(
        Storage.Migration(
            "Initial PlantBatchManager",
            List(
                sql"""
				CREATE TABLE Plants (
					ChunkX INTEGER NOT NULL,
					ChunkZ INTEGER NOT NULL,
					World UUID NOT NULL,
					OffsetX INTEGER NOT NULL,
					OffsetZ INTEGER NOT NULL,
					Y INTEGER NOT NULL,
					Kind TEXT NOT NULL,
					AgeIngameHours INTEGER NOT NULL,
					IncompleteGrowthAdvancements INTEGER NOT NULL,
					UNIQUE(ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y)
				);
				""".command
            ),
            List(
                sql"""
				DROP TABLE Plants;
				""".command
            ),
        )
    )

    sql.applyMigration(
        Storage.Migration(
            "Cache Plant Being Valid In The Database",
            List(
                sql"""
                ALTER TABLE Plants ADD COLUMN ValidClimate BOOLEAN;
                """.command,
                sql"""
                UPDATE Plants SET ValidClimate = 't';
                """.command,
                sql"""
                ALTER TABLE Plants ALTER COLUMN ValidClimate SET NOT NULL;
                """.command,
            ),
            List(
                sql"""
                DROP TABLE Plants;
                """.command
            ),
        )
    )

    private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
        (x / 16, z / 16, x % 16, z % 16)

    private def fromOffsets(
        chunkX: Int,
        chunkZ: Int,
        offsetX: Int,
        offsetZ: Int,
    ): (Int, Int) =
        ((chunkX * 16) + offsetX, (chunkZ * 16) + offsetZ)

    case class ChunkKey(cx: Int, cz: Int, world: UUID)
    case class OffsetKey(dx: Int, dz: Int, y: Int)

    private val plants =
        TrieMap[ChunkKey, Map[OffsetKey, Dirty[DBPlantData]]]()

    private def saveAll()(using Session[IO]): IO[Unit] =
        plants.flatMap((_, chunk) => chunk).toList.traverse_ { (_, plant) =>
            save(plant)
        }

    private def load()(using Session[IO]): IO[Unit] =
        for {
            plants <- sql.queryListIO(
                sql"""
			SELECT ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y, Kind, AgeIngameHours, IncompleteGrowthAdvancements, ValidClimate FROM Plants
			""",
                (int4 *: int4 *: uuid *: int4 *: int4 *: int4 *: plantKindCodec *: int4 *: int4 *: bool)
                    .to[DBPlantData],
                skunk.Void,
            )
        } yield {
            plants.foreach { pd =>
                set(
                    pd.chunkX,
                    pd.chunkZ,
                    pd.world,
                    pd.offsetX,
                    pd.offsetZ,
                    pd.yPos,
                    Dirty(pd, false, false),
                )
            }
        }

    private def save(value: Dirty[DBPlantData])(using Session[IO]): IO[Unit] =
        if value.deleted then
            sql
                .commandIO(
                    sql"""
			DELETE FROM Plants
				WHERE ChunkX = $int4
				  AND ChunkZ = $int4
				  AND World = $uuid
				  AND OffsetX = $int4
				  AND OffsetZ = $int4
				  AND Y = $int4;
			""",
                    (
                        value.inner.chunkX,
                        value.inner.chunkZ,
                        value.inner.world,
                        value.inner.offsetX,
                        value.inner.offsetZ,
                        value.inner.yPos,
                    ),
                )
                .map(_ => ())
        else
            sql
                .commandIO(
                    sql"""
			INSERT INTO Plants
				(ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y, Kind, AgeIngameHours, IncompleteGrowthAdvancements, ValidClimate)
			VALUES
				($int4, $int4, $uuid, $int4, $int4, $int4, $plantKindCodec, $int4, $int4, $bool)
			ON CONFLICT (ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y) DO UPDATE SET
				Kind = EXCLUDED.Kind, AgeIngameHours = EXCLUDED.AgeIngameHours, IncompleteGrowthAdvancements = EXCLUDED.IncompleteGrowthAdvancements;
			""",
                    (
                        value.inner.chunkX,
                        value.inner.chunkZ,
                        value.inner.world,
                        value.inner.offsetX,
                        value.inner.offsetZ,
                        value.inner.yPos,
                        value.inner.what,
                        value.inner.ageIngameHours,
                        value.inner.incompleteGrowthAdvancements,
                        value.inner.isInValidClimate,
                    ),
                )
                .map(_ => ())

    private def set(
        cx: Int,
        cz: Int,
        w: UUID,
        ox: Int,
        oz: Int,
        y: Int,
        f: Dirty[DBPlantData],
    ): Unit =
        plants(ChunkKey(cx, cz, w)) = plants.getOrElse(
            ChunkKey(cx, cz, w),
            Map(),
        ) + (OffsetKey(ox, oz, y) -> f)

    private def incrementPlantAges(): Unit =
        // increment plant ages and their growth ticks if necessary
        plants.mapValuesInPlace { (key, map) =>
            map.map { (plantKey, plant) =>
                if plant.deleted then plantKey -> plant
                else
                    val (x, z) = fromOffsets(
                        plant.inner.chunkX,
                        plant.inner.chunkZ,
                        plant.inner.offsetX,
                        plant.inner.offsetZ,
                    )
                    val rightSeason =
                        plant.inner.what.growingSeason growsWithin Datekeeping
                            .time()
                            .month
                            .toInt
                            .pipe(Month.fromOrdinal)
                            .season
                    val timePassed =
                        (plant.inner.ageIngameHours + 1) % plant.inner.what.plant
                            .hours() == 0
                    val incrBy =
                        if rightSeason && plant.inner.isInValidClimate && timePassed
                        then 1
                        else 0
                    plantKey -> plant.copy(
                        inner = plant.inner.copy(
                            ageIngameHours = plant.inner.ageIngameHours + 1,
                            incompleteGrowthAdvancements =
                                plant.inner.incompleteGrowthAdvancements + incrBy,
                        ),
                        dirty = true,
                    )
            }
        }

    private def doWorkForChunk(
        world: World,
        map: Map[OffsetKey, Dirty[DBPlantData]],
    ): Map[OffsetKey, Dirty[DBPlantData]] =
        map.view.mapValues { case (data) =>
            if !data.deleted && data.inner.incompleteGrowthAdvancements > 0 then
                val (x, z) = fromOffsets(
                    data.inner.chunkX,
                    data.inner.chunkZ,
                    data.inner.offsetX,
                    data.inner.offsetZ,
                )
                val block =
                    world.getBlockAt(
                        x,
                        data.inner.yPos,
                        z,
                    )

                val done = 1
                    .to(
                        data.inner.incompleteGrowthAdvancements
                    )
                    .exists { _ =>
                        val result =
                            PlantGrower.grow(
                                block,
                                data.inner.what.plant,
                            )
                        result.isSuccess && result.get
                    }
                if done then data.copy(deleted = true)
                else
                    data.copy(inner =
                        data.inner.copy(incompleteGrowthAdvancements = 0)
                    )
            else data
        }.toMap

    private def doTickPlants(): Unit =
        incrementPlantAges()

        val bossBar = BossBar.bossBar(
            txt"Ticking plants...",
            0.0f,
            BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS,
        )
        bossBar.addViewer(Bukkit.getServer())

        def progressBarPipe(size: Int): fs2.Pipe[IO, ChunkKey, ChunkKey] = {
            input =>
                input.zipWithIndex
                    .evalTap { case (_, index) =>
                        IO { bossBar.progress(index.toFloat / size.toFloat) }
                    }
                    .map(_._1)
        }

        fs2.Stream
            .iterable[IO, ChunkKey](plants.keys)
            .through(progressBarPipe(plants.keys.size))
            .parEvalMap(10) { chunkKey =>
                for {
                    world <- IO { Bukkit.getWorld(chunkKey.world) }
                    _ <- IO { world.isChunkLoaded(chunkKey.cx, chunkKey.cz) }
                        .ifM(
                            IO {
                                plants.updateWith(chunkKey) {
                                    case Some(map) =>
                                        Some(doWorkForChunk(world, map))
                                    case None => None
                                }
                            }.evalOn(
                                ChunkExecutionContext(
                                    chunkKey.cx,
                                    chunkKey.cz,
                                    world,
                                )
                            ),
                            IO.pure(()),
                        )
                } yield ()
            }
            .compile
            .drain
            .pipe(sql.useBlocking)

        val _ = bossBar.removeViewer(Bukkit.getServer())

    def handle(m: PlantMsg): Unit =
        m match
            case PlantMsg.startGrowing(what, where) =>
                val (cx, cz, ox, oz) = toOffsets(where.getX, where.getZ)
                val y = where.getY
                val world = where.getWorld.getUID

                given ec: ExecutionContext = ChunkExecutionContext(
                    cx,
                    cz,
                    where.getWorld,
                )
                val rightClimate =
                    what.growingClimate growsWithin Await
                        .result(
                            Future {
                                Climate.climateAt(
                                    where.getX,
                                    where.getY,
                                    where.getZ,
                                )
                            },
                            1.seconds,
                        )

                set(
                    cx,
                    cz,
                    world,
                    ox,
                    oz,
                    y,
                    Dirty(
                        DBPlantData(
                            cx,
                            cz,
                            world,
                            ox,
                            oz,
                            y,
                            what,
                            0,
                            0,
                            rightClimate,
                        ),
                        true,
                        false,
                    ),
                )
            case PlantMsg.stopGrowing(where) =>
                val (cx, cz, ox, oz) = toOffsets(where.getX, where.getZ)
                val y = where.getY
                val world = where.getWorld.getUID

                val chunkKey = ChunkKey(cx, cz, world)
                val offsetKey = OffsetKey(ox, oz, y)

                val _ = plants.updateWith(chunkKey) {
                    _.map(_.updatedWith(offsetKey) {
                        _.map(_.copy(deleted = true))
                    })
                }
            case PlantMsg.inspect(where, player) =>
                val (cx, cz, ox, oz) = toOffsets(where.getX, where.getZ)
                val y = where.getY
                val world = where.getWorld.getUID

                given ec: ExecutionContext = LocationExecutionContext(
                    where.getLocation()
                )

                val chunkKey = ChunkKey(cx, cz, world)
                val offsetKey = OffsetKey(ox, oz, y)

                val plant = plants
                    .get(chunkKey)
                    .flatMap(_.get(offsetKey))
                    .filterNot(_.deleted)
                plant.foreach { plant =>
                    FireAndForget {
                        val (x, z) = fromOffsets(
                            plant.inner.chunkX,
                            plant.inner.chunkZ,
                            plant.inner.offsetX,
                            plant.inner.offsetZ,
                        )
                        val actualSeason =
                            Datekeeping
                                .time()
                                .month
                                .toInt
                                .pipe(Month.fromOrdinal)
                                .season
                        val actualClimate =
                            Climate.climateAt(x, plant.inner.yPos, z)
                        val rightSeason =
                            plant.inner.what.growingSeason growsWithin actualSeason
                        val rightClimate =
                            plant.inner.what.growingClimate growsWithin actualClimate

                        (rightSeason, rightClimate) match
                            case (false, false) =>
                                player.sendServerMessage(
                                    txt"This plant is neither in the right climate nor season; it grows in ${plant.inner.what.growingClimate.display} climates during ${plant.inner.what.growingSeason.display}, but it is in a ${actualClimate.display} climate and it is currently ${actualSeason.display}"
                                )
                            case (false, true) =>
                                player.sendServerMessage(
                                    txt"This plant is out of season; it grows during ${plant.inner.what.growingSeason.display} (it is currently ${actualSeason.display})"
                                )
                            case (true, false) =>
                                player.sendServerMessage(
                                    txt"This plant is in the wrong climate; it grows in ${plant.inner.what.growingClimate.display} climates (it is in a ${actualClimate.display} climate)"
                                )
                            case (true, true) =>
                                player.sendServerMessage(
                                    txt"This plant is in the right season and climate! It is ${plant.inner.ageIngameHours} hours old."
                                )
                    }
                }
            case PlantMsg.tickPlants =>
                val transaction =
                    Sentry.startTransaction("PlantMsg.tickPlants", "operation")

                try doTickPlants()
                catch
                    case e: Throwable =>
                        transaction.setThrowable(e)
                        transaction.setStatus(SpanStatus.NOT_FOUND)
                finally transaction.finish()
            case PlantMsg.chunkLoaded(chunk) =>
                val cx = chunk.getX()
                val cz = chunk.getZ()
                val world = chunk.getWorld()
                val key = ChunkKey(cx, cz, world.getUID())
                val _ = plants.updateWith(key) {
                    case Some(map) =>
                        sql.useBlocking(IO {
                            Some(doWorkForChunk(world, map))
                        }.evalOn(ChunkExecutionContext(cx, cz, world)))
                    case None => None
                }
