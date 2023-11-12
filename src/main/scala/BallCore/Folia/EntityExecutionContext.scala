// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Folia

import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

import java.util.logging.Level
import scala.concurrent.ExecutionContext

class EntityExecutionContext(ent: Entity)(using plugin: Plugin)
    extends ExecutionContext:
    val sched: EntityScheduler = ent.getScheduler

    override def execute(runnable: Runnable): Unit =
        val _ = sched.run(plugin, _ => runnable.run(), null)

    override def reportFailure(cause: Throwable): Unit =
        plugin.getLogger
            .log(
                Level.WARNING,
                s"Error in EntityExecutionContext for $ent:",
                cause,
            )
