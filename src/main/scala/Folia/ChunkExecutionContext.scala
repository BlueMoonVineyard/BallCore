package BallCore.Folia

import org.bukkit.entity.Entity
import scala.concurrent.ExecutionContext
import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import org.bukkit.plugin.Plugin
import java.util.logging.Level
import org.bukkit.World

class ChunkExecutionContext(cx: Int, cz: Int, world: World)(using plugin: Plugin) extends ExecutionContext:
    val sched = plugin.getServer().getRegionScheduler()

    override def execute(runnable: Runnable): Unit =
        sched.run(plugin, world, cx, cz, _ => runnable.run())
    override def reportFailure(cause: Throwable): Unit =
        plugin.getLogger().log(Level.WARNING, s"Error in ChunkExecutionContext for ${cx}/${cz} in ${world}:", cause)
