package BallCore.DataStructures

object Delay:
    import java.util.{Timer, TimerTask}
    import java.util.Date
    import scala.concurrent._
    import scala.concurrent.duration.FiniteDuration
    import scala.util.Try

    private val timer = new Timer(true)

    def by(delay: FiniteDuration)(implicit ctx: ExecutionContext): Future[Unit] =
        val prom = Promise[Unit]()
        val task =
            new TimerTask:
                def run(): Unit =
                    ctx.execute(
                        new Runnable:
                            def run(): Unit =
                                prom.complete(Try(()))
                    )
        timer.schedule(task, delay.toMillis)
        prom.future