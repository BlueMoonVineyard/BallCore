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
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.chaining.*
import io.sentry.Sentry
import io.sentry.SpanStatus
import net.kyori.adventure.bossbar.BossBar

enum PlantMsg:
    case startGrowing(what: Plant, where: Block)
    case stopGrowing(where: Block)
    case tickPlants
    case inspect(where: Block, player: Player)

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

    // private def chunkAt(world: World, cx: Int, cz: Int)(using
    //     p: Plugin
    // ): ResourceIO[Chunk] =
    //     Resource.make {
    //         IO.fromCompletableFuture {
    //             IO {
    //                 world
    //                     .getChunkAtAsync(cx, cz)
    //                     .thenApply(chunk =>
    //                         chunk.tap(_.addPluginChunkTicket(p))
    //                     )
    //             }
    //         }
    //     } { chunk =>
    //         IO { val _ = chunk.removePluginChunkTicket(p) }
    //     }

    // private def chunksNearby(world: World, cx: Int, cz: Int)(using
    //     p: Plugin
    // ): ResourceIO[Chunk] =
    //     for {
    //         a <- chunkAt(world, cx + -1, cz + -1)
    //         b <- chunkAt(world, cx + -1, cz + 0)
    //         c <- chunkAt(world, cx + -1, cz + 1)
    //         d <- chunkAt(world, cx + 0, cz + -1)
    //         centreChunk <- chunkAt(world, cx + 0, cz + 0)
    //         f <- chunkAt(world, cx + 0, cz + 1)
    //         g <- chunkAt(world, cx + 1, cz + -1)
    //         h <- chunkAt(world, cx + 1, cz + 0)
    //         i <- chunkAt(world, cx + 1, cz + 1)
    //     } yield centreChunk

    private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
        (x / 16, z / 16, x % 16, z % 16)

    private def fromOffsets(
        chunkX: Int,
        chunkZ: Int,
        offsetX: Int,
        offsetZ: Int,
    ): (Int, Int) =
        ((chunkX * 16) + offsetX, (chunkZ * 16) + offsetZ)

    private val plants =
        mutable
            .Map[(Int, Int, UUID), Map[(Int, Int, Int), Dirty[DBPlantData]]]()

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
            IO.println("saving deleted plant") *>
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
            IO.println("saving non-deleted plant") *>
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

    private def get(
        cx: Int,
        cz: Int,
        w: UUID,
    ): Map[(Int, Int, Int), Dirty[DBPlantData]] =
        plants.getOrElse((cx, cz, w), Map())

    private def set(
        cx: Int,
        cz: Int,
        w: UUID,
        ox: Int,
        oz: Int,
        y: Int,
        f: Dirty[DBPlantData],
    ): Unit =
        plants((cx, cz, w)) = get(cx, cz, w) + ((ox, oz, y) -> f)

    private def update(cx: Int, cz: Int, w: UUID, ox: Int, oz: Int, y: Int)(
        f: Dirty[DBPlantData] => Dirty[DBPlantData]
    ): Unit =
        val old = get(cx, cz, w)
        old.get((ox, oz, y)) match
            case None =>
            case Some(value) =>
                plants((cx, cz, w)) = get(cx, cz, w) + ((ox, oz, y) -> f(value))

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
                update(cx, cz, world, ox, oz, y) {
                    _.copy(deleted = true)
                }
            case PlantMsg.inspect(where, player) =>
                val (cx, cz, ox, oz) = toOffsets(where.getX, where.getZ)
                val y = where.getY
                val world = where.getWorld.getUID

                given ec: ExecutionContext = LocationExecutionContext(
                    where.getLocation()
                )

                FireAndForget {
                    get(cx, cz, world)
                        .get(ox, oz, y)
                        .filterNot(_.deleted)
                        .foreach { plant =>
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

                try
                    // increment plant ages and their growth ticks if necessary
                    val mapInPlaceSpan = transaction.startChild(
                        "computation",
                        "mapping values in place",
                    )
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
                                        ageIngameHours =
                                            plant.inner.ageIngameHours + 1,
                                        incompleteGrowthAdvancements =
                                            plant.inner.incompleteGrowthAdvancements + incrBy,
                                    ),
                                    dirty = true,
                                )
                        }
                    }
                    mapInPlaceSpan.finish()

                    val bossBar = BossBar.bossBar(
                        txt"Ticking plants...",
                        0.0f,
                        BossBar.Color.GREEN,
                        BossBar.Overlay.PROGRESS,
                    )
                    bossBar.addViewer(Bukkit.getServer())

                    val plantDispatchSpan = transaction.startChild(
                        "dispatching",
                        "dispatching plants to be grown",
                    )
                    val size = plants.size
                    // dispatch plants with growth ticks to be grown in the world
                    plants.zipWithIndex.foreach { case ((key, map), idx) =>
                        val (cx, cz, worldID) = key
                        val world = Bukkit.getWorld(worldID)
                        bossBar.progress(idx.toFloat / size.toFloat)

                        sql.useBlocking(IO {
                            if world.isChunkLoaded(cx, cz) then
                                val (done, notDone) = map.view
                                    .filterNot(_._2.deleted)
                                    .filter(
                                        _._2.inner.incompleteGrowthAdvancements > 0
                                    )
                                    .mapValues { data =>
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

                                        (data, block)
                                    }
                                    .toList
                                    .partition { (_, in) =>
                                        val (data, block) = in

                                        1.to(
                                            data.inner.incompleteGrowthAdvancements
                                        ).exists { _ =>
                                            val result = PlantGrower.grow(
                                                block,
                                                data.inner.what.plant,
                                            )
                                            result.isSuccess && result.get
                                        }
                                    }

                                done.map(_._2._2).foreach { blk =>
                                    val (cx, cz, ox, oz) =
                                        toOffsets(blk.getX, blk.getZ)
                                    val y = blk.getY
                                    val world = blk.getWorld.getUID
                                    update(cx, cz, world, ox, oz, y) {
                                        _.copy(deleted = true)
                                    }
                                }
                                notDone.map(_._2._2).foreach { blk =>
                                    val (cx, cz, ox, oz) =
                                        toOffsets(blk.getX, blk.getZ)
                                    val y = blk.getY
                                    val world = blk.getWorld.getUID
                                    update(cx, cz, world, ox, oz, y) { d =>
                                        d.copy(inner =
                                            d.inner
                                                .copy(
                                                    incompleteGrowthAdvancements = 0
                                                )
                                        )
                                    }
                                }
                        }.evalOn(ChunkExecutionContext(cx, cz, world)))
                    }
                    plantDispatchSpan.finish()

                    val _ = bossBar.removeViewer(Bukkit.getServer())
                catch
                    case e: Throwable =>
                        transaction.setThrowable(e)
                        transaction.setStatus(SpanStatus.NOT_FOUND)
                finally transaction.finish()
