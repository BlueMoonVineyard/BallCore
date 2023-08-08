package BallCore.Plants

import BallCore.Storage.SQLManager
import BallCore.DataStructures.Actor
import org.bukkit.block.Block
import BallCore.Storage

import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor
import java.util.UUID
import scala.util.chaining._
import BallCore.Folia.ChunkExecutionContext
import org.bukkit.World
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import scala.concurrent.Future
import scala.collection.mutable
import java.time.LocalDateTime
import BallCore.Datekeeping.DateUnit
import java.time.temporal.ChronoUnit
import BallCore.Datekeeping.Datekeeping.Periods
import java.util.concurrent.TimeUnit
import BallCore.Folia.FireAndForget

enum PlantMsg:
	case startGrowing(what: Plant, where: Block)
	case stopGrowing(where: Block)
	case plantsGrewPartially(where: List[Block])
	case plantsFinishedGrowing(where: List[Block])
	case tickPlants

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
	val dirty: Boolean,
	val deleted: Boolean,
)
object DBPlantData:
	def apply(ws: WrappedResultSet): DBPlantData =
		DBPlantData(
			ws.int("ChunkX"),
			ws.int("ChunkZ"),
			ws.string("World").pipe(UUID.fromString),
			ws.int("OffsetX"),
			ws.int("OffsetZ"),
			ws.int("Y"),
			ws.string("Kind").pipe(Plant.valueOf),
			ws.int("AgeIngameHours"),
			ws.int("IncompleteGrowthAdvancements"),
			false,
			false,
		)

class PlantBatchManager()(using sql: SQLManager, p: Plugin) extends Actor[PlantMsg]:
	def millisToNextHour(): Long =
		val nextHour = LocalDateTime.now().plus(1, DateUnit.hour).truncatedTo(DateUnit.hour)
		LocalDateTime.now().until(nextHour, ChronoUnit.MILLIS)

	protected def handleInit(): Unit =
		p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => send(PlantMsg.tickPlants), millisToNextHour(), Periods.hour.toMillis(), TimeUnit.MILLISECONDS)
		load()
	protected def handleShutdown(): Unit =
		saveAll()

	sql.applyMigration(
		Storage.Migration(
			"Initial PlantBatchManager",
			List(
				sql"""
				CREATE TABLE Plants (
					ChunkX INTEGER NOT NULL,
					ChunkZ INTEGER NOT NULL,
					World TEXT NOT NULL,
					OffsetX INTEGER NOT NULL,
					OffsetZ INTEGER NOT NULL,
					Y INTEGER NOT NULL,
					Kind TEXT NOT NULL,
					AgeIngameHours INTEGER NOT NULL,
					IncompleteGrowthAdvancements INTEGER NOT NULL,
					UNIQUE(ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y)
				);
				"""
			),
			List(
				sql"""
				DROP TABLE Plants;
				"""
			),
		)
	)

	private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
		(x / 16, z / 16, x % 16, z % 16)
	private def fromOffsets(chunkX: Int, chunkZ: Int, offsetX: Int, offsetZ: Int): (Int, Int) =
		((chunkX*16) + offsetX, (chunkZ*16) + offsetZ)

	private val plants = mutable.Map[(Int, Int, UUID), Map[(Int, Int, Int), DBPlantData]]()
	private implicit val session: DBSession = sql.session

	private def saveAll(): Unit =
		plants.foreach { (_, chunk) =>
			chunk.foreach { (_, plant) =>
				save(plant)
			}
		}
	private def load(): Unit =
		sql"SELECT * FROM Plants"
			.foreach { row =>
				val pd = DBPlantData(row)
				set(pd.chunkX, pd.chunkZ, pd.world, pd.offsetX, pd.offsetZ, pd.yPos, pd)
			}
	private def save(value: DBPlantData): Unit =
		if value.deleted then
			sql"""
			DELETE FROM Plants
				WHERE ChunkX = ${value.chunkX}
				  AND ChunkZ = ${value.chunkZ}
				  AND World = ${value.world}
				  AND OffsetX = ${value.offsetX}
				  AND OffsetZ = ${value.offsetZ}
				  AND Y = ${value.yPos};
			""".update.apply()
		else
			sql"""
			INSERT OR REPLACE INTO Plants
				(ChunkX, ChunkZ, World, OffsetX, OffsetZ, Y, Kind, AgeIngameHours, IncompleteGrowthAdvancements)
			VALUES
				(${value.chunkX}, ${value.chunkZ}, ${value.world}, ${value.offsetX}, ${value.offsetZ}, ${value.yPos}, ${value.what.toString()}, ${value.ageIngameHours}, ${value.incompleteGrowthAdvancements});
			""".update.apply()
	private def get(cx: Int, cz: Int, w: UUID): Map[(Int, Int, Int), DBPlantData] =
		plants.get((cx, cz, w)).getOrElse(Map())
	private def set(cx: Int, cz: Int, w: UUID, ox: Int, oz: Int, y: Int, f: DBPlantData): Unit =
		plants((cx, cz, w)) = get(cx, cz, w) + ((ox, oz, y) -> f)
	private def update(cx: Int, cz: Int, w: UUID, ox: Int, oz: Int, y: Int)(f: DBPlantData => DBPlantData): Unit =
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
				set(cx, cz, world, ox, oz, y, DBPlantData(cx, cz, world, ox, oz, y, what, 0, 0, true, false))
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
					update(cx, cz, world, ox, oz, y) { _.copy(incompleteGrowthAdvancements = 0) }
				}
			case PlantMsg.tickPlants =>
				// increment plant ages and their growth ticks if necessary
				plants.mapValuesInPlace{ (key, map) =>
					map.map{ (plantKey, plant) =>
						if plant.deleted then
							plantKey -> plant
						else
							val incrBy =
								if (plant.ageIngameHours + 1) % plant.what.plant.hours() == 0 then
									1
								else
									0
							plantKey -> plant.copy(ageIngameHours = plant.ageIngameHours + 1, incompleteGrowthAdvancements = plant.incompleteGrowthAdvancements + incrBy)
					}
				}

				// dispatch plants with growth ticks to be grown in the world
				plants.foreach { (key, map) =>
					val (cx, cz, worldID) = key
					val world = Bukkit.getWorld(worldID)
					given ec: ChunkExecutionContext = ChunkExecutionContext(cx, cz, world)

					FireAndForget {
						val (done, notDone) = map.filter(!_._2.deleted).mapValues { data =>
							val (x, z) = fromOffsets(data.chunkX, data.chunkZ, data.offsetX, data.offsetZ)
							val block = world.getBlockAt(x, data.yPos, z)

							(data, block)
						}.toList.partition { (key, in) =>
							val (data, block) = in

							1.to(data.incompleteGrowthAdvancements).exists { _ =>
								PlantGrower.grow(block, data.what.plant)
							}
						}
						this.send(PlantMsg.plantsFinishedGrowing(done.map(_._2._2)))
						this.send(PlantMsg.plantsGrewPartially(notDone.map(_._2._2)))
					}
				}

