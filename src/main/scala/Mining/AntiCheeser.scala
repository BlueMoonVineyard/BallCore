package BallCore.Mining

import BallCore.Storage.SQLManager
import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor
import BallCore.Storage.Migration
import org.bukkit.block.Block
import scala.util.chaining._

/*

# Anti-Cheesing

Anti-cheesing slices up chunks into y layers, initialising each layer's "health" with the number of non-air blocks
in that layer when it is first accessed.

Every chance to get something from breaking decreases the health by one.

*/

trait LayerCounter:
	def count(chunkX: Int, chunkZ: Int, y: Int): Int

class AntiCheeser()(using sql: SQLManager):
    sql.applyMigration(
        Migration(
            "Initial Mining AntiCheeser",
            List(
                sql"""
                CREATE TABLE MiningAntiCheeser (
                	ChunkX INT NOT NULL,
                	ChunkZ INT NOT NULL,
                	Y INT NOT NULL,
                	World TEXT NOT NULL,
                	Health INT NOT NULL,
                	UNIQUE(ChunkX, ChunkZ, Y, World)
                );
                """,
            ),
            List(
                sql"""
                DROP TABLE MiningAntiCheeser;
                """,
            ),
        )
    )
    private implicit val session: DBSession = sql.session

    def countNonAirBlocksInSlice(b: Block): Int =
    	???

    def blockBroken(b: Block): Boolean =
    	val cx = b.getChunk().getX()
    	val cz = b.getChunk().getZ()
    	val y = b.getY()
    	val world = b.getWorld().getUID()

    	val health =
    		sql"""
    		SELECT Health FROM MiningAntiCheeser WHERE ChunkX = ${cx} AND ChunkZ = ${cz} AND Y = ${y} AND World = ${world};
    		"""
    		.map(rs => rs.int("Health"))
    		.single
    		.apply()
    		.getOrElse(countNonAirBlocksInSlice(b))
    		.tap(_ - 1)

    	sql"""
    	INSERT OR REPLACE INTO MiningAntiCheeser (
    		Health, ChunkX, ChunkZ, Y, World
    	) VALUES (
    		${health.max(0)}, ${cx}, ${cz}, ${y}, ${world}
    	);
    	"""
    	.update
    	.apply()

    	health >= 0
