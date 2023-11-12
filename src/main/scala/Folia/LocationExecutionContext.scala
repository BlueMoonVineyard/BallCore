// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Folia

import org.bukkit.Location
import org.bukkit.plugin.Plugin

import java.util.logging.Level
import scala.concurrent.ExecutionContext

class LocationExecutionContext(loc: Location)(using plugin: Plugin)
  extends ExecutionContext:
  val sched = plugin.getServer().getRegionScheduler()

  override def execute(runnable: Runnable): Unit =
    sched.execute(plugin, loc, runnable)

  override def reportFailure(cause: Throwable): Unit =
    plugin
      .getLogger()
      .log(
        Level.WARNING,
        s"Error in LocationExecutionContext for ${loc}:",
        cause
      )
