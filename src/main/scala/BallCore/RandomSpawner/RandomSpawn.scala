package BallCore.RandomSpawner

import org.bukkit.Bukkit
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import org.bukkit.Location
import cats.effect.IO
import org.locationtech.jts.shape.random.RandomPointsBuilder
import BallCore.Folia.LocationExecutionContext
import org.bukkit.plugin.Plugin
import org.bukkit.block.Block
import cats.data.OptionT
import org.bukkit.block.BlockFace

class RandomSpawn()(using p: Plugin):
    val gf = GeometryFactory()
    val rectangle =
        gf.createPolygon(
            Array(
                Coordinate(-5000, -5000, 0),
                Coordinate(-5000, +5000, 0),
                Coordinate(+5000, -5000, 0),
                Coordinate(+5000, +5000, 0),
                Coordinate(-5000, -5000, 0),
            )
        )
    val world = Bukkit.getWorld("world")

    private def randomPoint: IO[Coordinate] =
        IO {
            val builder = RandomPointsBuilder(gf)
            builder.setExtent(rectangle)
            builder.setNumPoints(1)
            builder.getGeometry().getCoordinates().head
        }

    private def topBlockAt(coord: Coordinate): IO[Option[Block]] =
        val location = Location(world, coord.getX(), 320, coord.getY())
        IO {
            val block = world.getHighestBlockAt(location)
            Option(block).flatMap { block =>
                val upper = block.getRelative(BlockFace.UP)
                if block.isSolid() && upper.isPassable() && upper
                        .getRelative(BlockFace.UP)
                        .isPassable()
                then Some(upper)
                else None
            }
        }.evalOn(LocationExecutionContext(location))

    private def randomBlock: IO[Option[Block]] = randomPoint.flatMap(topBlockAt)

    def randomSpawnLocation: IO[Block] =
        OptionT(randomBlock).getOrElseF(randomSpawnLocation)
