package BallCore.Sigils

import BallCore.Beacons.BeaconID
import cats.effect.IO
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Geometry
import java.util.UUID
import org.locationtech.jts.geom.GeometryFactory
import org.bukkit.Bukkit
import cats.effect.cps._
import scala.concurrent.ExecutionContext
import BallCore.Folia.LocationExecutionContext
import org.bukkit.Location
import org.locationtech.jts.shape.random.RandomPointsBuilder
import org.locationtech.jts.geom.Point
import org.bukkit.plugin.Plugin
import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.EntityType
import scala.util.chaining._
import org.bukkit.util.Transformation
import org.joml.Vector3f
import org.joml.AxisAngle4f
import org.bukkit.entity.Interaction
import skunk.Session

object GameBattleHooks:
    def register()(using
        Plugin,
        CustomEntityManager,
        SlimePillarManager,
    ): GameBattleHooks =
        GameBattleHooks()

extension (i: IO.type)
    def on[A](ec: ExecutionContext)(thunk: => A): IO[A] =
        IO(thunk).evalOn(ec)

class GameBattleHooks(using
    p: Plugin,
    cem: CustomEntityManager,
    spm: SlimePillarManager,
) extends BattleHooks:
    private val gf = GeometryFactory()

    private def spawnPillarAt(block: Block, battle: BattleID)(using
        Session[IO]
    ): IO[Unit] =
        for {
            entities <- IO.on(LocationExecutionContext(block.getLocation())) {
                val world = block.getWorld()

                val targetXZ =
                    block.getLocation().clone().tap(_.add(0.5, 1, 0.5))

                val targetModelLocation = targetXZ
                    .clone()
                    .tap(
                        _.add(
                            0,
                            SlimePillar.scale - SlimePillar.heightBlocks,
                            0,
                        )
                    )
                val itemDisplay = world
                    .spawnEntity(targetModelLocation, EntityType.ITEM_DISPLAY)
                    .asInstanceOf[ItemDisplay]
                val scale = SlimePillar.scale
                itemDisplay.setTransformation(
                    Transformation(
                        Vector3f(),
                        AxisAngle4f(),
                        Vector3f(scale.toFloat),
                        AxisAngle4f(),
                    )
                )
                itemDisplay.setItemStack(SlimePillar.slimePillarModel)

                val interaction = world
                    .spawnEntity(targetXZ, EntityType.INTERACTION)
                    .asInstanceOf[Interaction]
                interaction.setInteractionHeight(
                    SlimePillar.totalHeightBlocks.toFloat
                )
                interaction.setInteractionWidth(
                    SlimePillar.heightBlocks.toFloat
                )
                interaction.setResponsive(true)

                (interaction, itemDisplay)
            }
            _ <- cem.addEntity(entities._1, entities._2, SlimePillar.entityKind)
            _ <- spm.addPillar(entities._1, battle)
        } yield ()

    private def randomPointIn(area: Geometry): IO[Point] =
        IO {
            val builder = RandomPointsBuilder(gf)
            builder.setExtent(area)
            builder.getGeometry().asInstanceOf[Point]
        }

    private def highestBlockAt(worldID: UUID, point: Point): IO[Block] =
        val world = Bukkit.getWorld(worldID)
        IO.on(
            LocationExecutionContext(
                Location(world, point.getX(), 0, point.getY())
            )
        ) {
            world.getHighestBlockAt(point.getX().toInt, point.getY().toInt)
        }

    override def spawnPillarFor(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        contestedArea: Geometry,
        worldID: UUID,
        defensiveBeacon: BeaconID,
    )(using Session[IO]): IO[Unit] =
        for {
            point <- randomPointIn(contestedArea)
            block <- highestBlockAt(worldID, point)
            _ <- spawnPillarAt(block, battle)
        } yield ()

    override def battleDefended(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
    )(using Session[IO]): IO[Unit] = ???

    override def battleTaken(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        newOffensiveArea: Polygon,
        defensiveBeacon: BeaconID,
    )(using Session[IO]): IO[Unit] = ???
