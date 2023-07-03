package BallCore.Folia

import org.bukkit.Location
import org.bukkit.plugin.Plugin
import scala.concurrent.ExecutionContext
import java.util.logging.Level

class LocationExecutionContext(loc: Location)(using plugin: Plugin) extends ExecutionContext:
    val sched = plugin.getServer().getRegionScheduler()

    override def execute(runnable: Runnable): Unit =
        sched.execute(plugin, loc, runnable)
    override def reportFailure(cause: Throwable): Unit =
        println(cause)
        plugin.getLogger().log(Level.WARNING, s"Error in LocationExecutionContext for ${loc}:", cause)
