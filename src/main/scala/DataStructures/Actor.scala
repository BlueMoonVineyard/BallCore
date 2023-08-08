package BallCore.DataStructures

import java.util.concurrent.LinkedTransferQueue
import scala.concurrent.Promise
import scala.util.Try
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

enum TrueMsg[Msg]:
	case inner(val msg: Msg)
	case shutdown(val promise: Promise[Unit])

class ShutdownCallbacks:
	var items = List[() => Future[Unit]]()
	def add(fn: () => Future[Unit]): Unit =
		items = items.appended(fn)
	def shutdown()(using ExecutionContext): Future[List[Unit]] =
		Future.sequence(items.map(_.apply()))

trait Actor[Msg]:
	def handle(m: Msg): Unit
	protected def handleInit(): Unit
	protected def handleShutdown(): Unit

	private val queue = LinkedTransferQueue[TrueMsg[Msg]]()

	def send(m: Msg): Unit =
		val _ = this.queue.add(TrueMsg.inner(m))
	def startListener()(using cb: ShutdownCallbacks): Unit =
		Thread.startVirtualThread(() => this.mainLoop())
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
					try
						handle(msg)
					catch
						case e: Exception => e.printStackTrace()
				case TrueMsg.shutdown(promise) =>
					promise.complete(Try {
						handleShutdown()
					})
