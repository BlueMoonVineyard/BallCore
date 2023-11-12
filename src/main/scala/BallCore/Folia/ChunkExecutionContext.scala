// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Folia

import io.papermc.paper.threadedregions.scheduler.RegionScheduler
import org.bukkit.World
import org.bukkit.plugin.Plugin

import java.util.logging.Level
import scala.concurrent.ExecutionContext

class ChunkExecutionContext(cx: Int, cz: Int, world: World)(using
                                                            plugin: Plugin
) extends ExecutionContext:
  val sched: RegionScheduler = plugin.getServer.getRegionScheduler

  override def execute(runnable: Runnable): Unit =
    val _ = sched.run(plugin, world, cx, cz, _ => runnable.run())

  override def reportFailure(cause: Throwable): Unit =
    plugin.getLogger
      .log(
        Level.WARNING,
        s"Error in ChunkExecutionContext for $cx/$cz in $world:",
        cause
      )
