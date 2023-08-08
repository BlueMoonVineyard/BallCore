package BallCore.Folia

import org.bukkit.entity.Entity
import scala.concurrent.ExecutionContext
import org.bukkit.plugin.Plugin
import java.util.logging.Level

class EntityExecutionContext(ent: Entity)(using plugin: Plugin) extends ExecutionContext:
    val sched = ent.getScheduler()

    override def execute(runnable: Runnable): Unit =
        val _ = sched.run(plugin, _ => runnable.run(), null)
    override def reportFailure(cause: Throwable): Unit =
        plugin.getLogger().log(Level.WARNING, s"Error in EntityExecutionContext for ${ent}:", cause)
