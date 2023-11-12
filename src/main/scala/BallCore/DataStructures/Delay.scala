// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.DataStructures

object Delay:

    import java.util.{Timer, TimerTask}
    import scala.concurrent.*
    import scala.concurrent.duration.FiniteDuration
    import scala.util.Try

    private val timer = new Timer(true)

    def by(delay: FiniteDuration)(implicit
        ctx: ExecutionContext
    ): Future[Unit] =
        val prom = Promise[Unit]()
        val task =
            new TimerTask:
                def run(): Unit =
                    ctx.execute(() => prom.complete(Try(())))
        timer.schedule(task, delay.toMillis)
        prom.future
