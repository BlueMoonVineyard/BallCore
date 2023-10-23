// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Plants

import BallCore.Storage.SQLManager
import BallCore.DataStructures.Actor
import org.bukkit.block.Block
import BallCore.Storage

import skunk.implicits._
import skunk.codec.all._
import cats.effect.IO
import cats.syntax.all._
import cats.effect._
import skunk.Session
import java.util.UUID
import scala.util.chaining._
import BallCore.Folia.ChunkExecutionContext
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import scala.collection.mutable
import java.time.LocalDateTime
import BallCore.Datekeeping.DateUnit
import java.time.temporal.ChronoUnit
import BallCore.Datekeeping.Datekeeping.Periods
import java.util.concurrent.TimeUnit
import BallCore.Folia.FireAndForget
import BallCore.DataStructures.Clock
import BallCore.Datekeeping.Datekeeping
import BallCore.Datekeeping.Month
import org.bukkit.entity.Player
import BallCore.Folia.LocationExecutionContext
import scala.concurrent.ExecutionContext
import BallCore.UI.ChatElements._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

enum PlantMsg:
	case startGrowing(what: Plant, where: Block)
	case stopGrowing(where: Block)
	case plantsGrewPartially(where: List[Block])
	case plantsFinishedGrowing(where: List[Block])
	case tickPlants
	case inspect(where: Block, player: Player)

case class DBPlantData(
	val chunkX: Int,
	val chunkZ: Int,
	val world: UUID,
	val offsetX: Int,
	val offsetZ: Int,
	val yPos: Int,
	val what: Plant,
	val ageIngameHours: Int,
	val incompleteGrowthAdvancements: Int,
)
case class Dirty[A] (
	val inner: A,
	val dirty: Boolean,
	val deleted: Boolean,
)

val plantKindCodec = text.imap { str => Plant.valueOf(str) } { it => it.toString() }

class PlantBatchManager()(using sql: SQLManager, p: Plugin, c: Clock) extends Actor[PlantMsg]:
	def millisToNextHour(): Long =
		val nextHour = LocalDateTime.now().plus(1, DateUnit.hour).truncatedTo(DateUnit.hour)
		LocalDateTime.now().until(nextHour, ChronoUnit.MILLIS)

	protected def handleInit(): Unit =
		p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => send(PlantMsg.tickPlants), millisToNextHour(), Periods.hour.toMillis(), TimeUnit.MILLISECONDS)
		sql.useBlocking(load())
	protected def handleShutdown(): Unit =
		sql.useBlocking(saveAll())

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

	private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
		(x / 16, z / 16, x % 16, z % 16)
	private def fromOffsets(chunkX: Int, chunkZ: Int, offsetX: Int, offsetZ: Int): (Int, Int) =
		((chunkX*16) + offsetX, (chunkZ*16) + offsetZ)

	private val plants = mutable.Map[(Int, Int, UUID), Map[(Int, Int, Int), Dirty[DBPlantData]]]()

	private def saveAll()(using Session[IO]): IO[Unit] =
		plants.flatMap((_, chunk) => chunk).toList.traverse_ { (_, plant) =>
			save(plant)
		}
	private def load()(using Session[IO]): IO[Unit] =
		for {
			plants <- sql.queryListIO(sql"""
			SELECT ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y, Kind, AgeIngameHours, IncompleteGrowthAdvancements FROM Plants
			""", (int4 *: int4 *: uuid *: int4 *: int4 *: int4 *: plantKindCodec *: int4 *: int4).to[DBPlantData], skunk.Void)
		} yield {
			plants.foreach { pd =>
				set(pd.chunkX, pd.chunkZ, pd.world, pd.offsetX, pd.offsetZ, pd.yPos, Dirty(pd, false, false))
			}
		}
	private def save(value: Dirty[DBPlantData])(using Session[IO]): IO[Unit] =
		if value.deleted then
			sql.commandIO(sql"""
			DELETE FROM Plants
				WHERE ChunkX = $int4
				  AND ChunkZ = $int4
				  AND World = $uuid
				  AND OffsetX = $int4
				  AND OffsetZ = $int4
				  AND Y = $int4;
			""", (value.inner.chunkX, value.inner.chunkZ, value.inner.world, value.inner.offsetX, value.inner.offsetZ, value.inner.yPos)).map(_ => ())
		else
			sql.commandIO(sql"""
			INSERT OR REPLACE INTO Plants
				(ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y, Kind, AgeIngameHours, IncompleteGrowthAdvancements)
			VALUES
				($int4, $int4, $uuid, $int4, $int4, $int4, $plantKindCodec, $int4, $int4)
			ON CONFLICT (ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y) DO UPDATE SET
				Kind = EXCLUDED.Kind, AgeIngameHours = EXCLUDED.AgeIngameHours, IncompleteGrowthAdvancements = EXCLUDED.IncompleteGrowthAdvancements;
			""", (value.inner.chunkX, value.inner.chunkZ, value.inner.world, value.inner.offsetX, value.inner.offsetZ, value.inner.yPos, value.inner.what, value.inner.ageIngameHours, value.inner.incompleteGrowthAdvancements)).map(_ => ())
	private def get(cx: Int, cz: Int, w: UUID): Map[(Int, Int, Int), Dirty[DBPlantData]] =
		plants.get((cx, cz, w)).getOrElse(Map())
	private def set(cx: Int, cz: Int, w: UUID, ox: Int, oz: Int, y: Int, f: Dirty[DBPlantData]): Unit =
		plants((cx, cz, w)) = get(cx, cz, w) + ((ox, oz, y) -> f)
	private def update(cx: Int, cz: Int, w: UUID, ox: Int, oz: Int, y: Int)(f: Dirty[DBPlantData] => Dirty[DBPlantData]): Unit =
		val old = get(cx, cz, w)
		old.get((ox, oz, y)) match
			case None =>
			case Some(value) =>
				plants((cx, cz, w)) = get(cx, cz, w) + ((ox, oz, y) -> f(value))

	def handle(m: PlantMsg): Unit =
		m match
			case PlantMsg.startGrowing(what, where) =>
				val (cx, cz, ox, oz) = toOffsets(where.getX(), where.getZ())
				val y = where.getY()
				val world = where.getWorld().getUID()
				set(cx, cz, world, ox, oz, y, Dirty(DBPlantData(cx, cz, world, ox, oz, y, what, 0, 0), true, false))
			case PlantMsg.stopGrowing(where) =>
				val (cx, cz, ox, oz) = toOffsets(where.getX(), where.getZ())
				val y = where.getY()
				val world = where.getWorld().getUID()
				update(cx, cz, world, ox, oz, y) { _.copy(deleted = true) }
			case PlantMsg.plantsFinishedGrowing(where) =>
				where.foreach { blk =>
					val (cx, cz, ox, oz) = toOffsets(blk.getX(), blk.getZ())
					val y = blk.getY()
					val world = blk.getWorld().getUID()
					update(cx, cz, world, ox, oz, y) { _.copy(deleted = true) }
				}
			case PlantMsg.plantsGrewPartially(where) =>
				where.foreach { blk =>
					val (cx, cz, ox, oz) = toOffsets(blk.getX(), blk.getZ())
					val y = blk.getY()
					val world = blk.getWorld().getUID()
					update(cx, cz, world, ox, oz, y) { d => d.copy(inner = d.inner.copy(incompleteGrowthAdvancements = 0)) }
				}
			case PlantMsg.inspect(where, player) =>
				val (cx, cz, ox, oz) = toOffsets(where.getX(), where.getZ())
				val y = where.getY()
				val world = where.getWorld().getUID()
				given ec: ExecutionContext = LocationExecutionContext(where.getLocation())
				FireAndForget {
					get(cx, cz, world).get(ox, oz, y).foreach { plant =>
						val (x, z) = fromOffsets(plant.inner.chunkX, plant.inner.chunkZ, plant.inner.offsetX, plant.inner.offsetZ)
						val actualSeason = Datekeeping.time().month.toInt.pipe(Month.fromOrdinal).season
						val actualClimate = Climate.climateAt(x, plant.inner.yPos, z)
						val rightSeason = plant.inner.what.growingSeason == actualSeason
						val rightClimate = plant.inner.what.growingClimate == actualClimate

						(rightSeason, rightClimate) match
							case (false, false) =>
								player.sendServerMessage(txt"This plant is neither in the right climate nor season; it grows in ${plant.inner.what.growingClimate.display} climates during ${plant.inner.what.growingSeason}, but it is in a ${actualClimate.display} climate and it is currently ${actualSeason}")
							case (false, true) =>
								player.sendServerMessage(txt"This plant is out of season; it grows during ${plant.inner.what.growingSeason} (it is currently ${actualSeason})")
							case (true, false) =>
								player.sendServerMessage(txt"This plant is in the wrong climate; it grows in ${plant.inner.what.growingClimate.display} climates (it is in a ${actualClimate.display} climate)")
							case (true, true) =>
								player.sendServerMessage(txt"This plant is in the right season and climate!")
					}
				}
			case PlantMsg.tickPlants =>
				// increment plant ages and their growth ticks if necessary
				plants.mapValuesInPlace{ (key, map) =>
					map.map{ (plantKey, plant) =>
						if plant.deleted then
							plantKey -> plant
						else
							val (x, z) = fromOffsets(plant.inner.chunkX, plant.inner.chunkZ, plant.inner.offsetX, plant.inner.offsetZ)
							given ec: ExecutionContext = ChunkExecutionContext(plant.inner.chunkX, plant.inner.chunkZ, Bukkit.getWorld(plant.inner.world))

							val rightSeason = plant.inner.what.growingSeason == Datekeeping.time().month.toInt.pipe(Month.fromOrdinal).season
							val rightClimate = plant.inner.what.growingClimate == Await.result(Future { Climate.climateAt(x, plant.inner.yPos, z) }, 1.seconds)
							val timePassed = (plant.inner.ageIngameHours + 1) % plant.inner.what.plant.hours() == 0
							val incrBy =
								if rightSeason && rightClimate && timePassed then
									1
								else
									0
							plantKey -> plant.copy(inner = plant.inner.copy(ageIngameHours = plant.inner.ageIngameHours + 1, incompleteGrowthAdvancements = plant.inner.incompleteGrowthAdvancements + incrBy), dirty = true)
					}
				}

				// dispatch plants with growth ticks to be grown in the world
				plants.foreach { (key, map) =>
					val (cx, cz, worldID) = key
					val world = Bukkit.getWorld(worldID)
					given ec: ChunkExecutionContext = ChunkExecutionContext(cx, cz, world)

					FireAndForget {
						val (done, notDone) = map.filter(!_._2.deleted).view.mapValues { data =>
							val (x, z) = fromOffsets(data.inner.chunkX, data.inner.chunkZ, data.inner.offsetX, data.inner.offsetZ)
							val block = world.getBlockAt(x, data.inner.yPos, z)

							(data, block)
						}.toList.partition { (_, in) =>
							val (data, block) = in

							1.to(data.inner.incompleteGrowthAdvancements).exists { _ =>
								PlantGrower.grow(block, data.inner.what.plant)
							}
						}
						this.send(PlantMsg.plantsFinishedGrowing(done.map(_._2._2)))
						this.send(PlantMsg.plantsGrewPartially(notDone.map(_._2._2)))
					}
				}

