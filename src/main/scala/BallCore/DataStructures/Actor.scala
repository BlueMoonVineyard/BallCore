// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.DataStructures

import java.util.concurrent.LinkedTransferQueue
import scala.concurrent.{Future, Promise}
import scala.util.Try
import cats.effect.IO
import io.sentry.Sentry
import cats.syntax.all._

enum TrueMsg[Msg]:
    case inner(val msg: Msg)
    case shutdown(val promise: Promise[Unit])

class ShutdownCallbacks:
    var items: List[IO[Unit]] = List[IO[Unit]]()
    var resources: List[IO[Unit]] = List[IO[Unit]]()

    def add(fn: () => Future[Unit]): Unit =
        items = items.appended(IO.fromFuture { IO { fn() } })

    def addIO[A](fn: (A, IO[Unit])): A =
        resources = resources.appended(fn._2)
        fn._1

    def shutdown(): IO[Unit] =
        items
            .concat(resources)
            .map { cb =>
                cb.recoverWith { case e: Throwable =>
                    IO {
                        Sentry.captureException(e)
                        println(s"Failed to run shutdown callback")
                        e.printStackTrace()
                    }
                }
            }
            .sequence_

trait Actor[Msg]:
    def handle(m: Msg): Unit

    protected def handleInit(): Unit

    protected def handleShutdown(): Unit

    private val queue = LinkedTransferQueue[TrueMsg[Msg]]()

    def send(m: Msg): Unit =
        val _ = this.queue.add(TrueMsg.inner(m))

    def startListener()(using cb: ShutdownCallbacks): Unit =
        val thread = Thread.startVirtualThread(() =>
            try
                println(
                    s"Actor Thread for ${this.getClass().getSimpleName()} is starting"
                )
                this.mainLoop()
            catch
                case e: Throwable =>
                    Sentry.captureException(e)
                    e.printStackTrace()
            finally
                println(
                    s"Actor Thread for ${this.getClass().getSimpleName()} is shutting down"
                )
        )
        thread.setName(s"Actor Thread: ${this.getClass().getSimpleName()}")
        cb.add(this.shutdown)

    def shutdown(): Future[Unit] =
        val prom = Promise[Unit]()
        this.queue.add(TrueMsg.shutdown(prom))
        prom.future

    private def mainLoop(): Unit =
        handleInit()
        while true do
            val msg = this.queue.take()
            msg match
                case TrueMsg.inner(msg) =>
                    try handle(msg)
                    catch
                        case e: Throwable =>
                            Sentry.captureException(e)
                            e.printStackTrace()
                case TrueMsg.shutdown(promise) =>
                    promise.complete(Try {
                        handleShutdown()
                    }.recover { case e =>
                        Sentry.captureException(e)
                        e.printStackTrace()
                    })
