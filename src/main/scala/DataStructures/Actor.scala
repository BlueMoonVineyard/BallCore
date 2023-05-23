package BallCore.DataStructures

import java.util.concurrent.LinkedTransferQueue
import scala.concurrent.Promise
import scala.util.Try
import scala.concurrent.Future

enum TrueMsg[Msg]:
	case inner(val msg: Msg)
	case shutdown(val promise: Promise[Unit])

trait Actor[Msg]:
	def handle(m: Msg): Unit
	protected def handleInit(): Unit
	protected def handleShutdown(): Unit

	private val queue = LinkedTransferQueue[TrueMsg[Msg]]()

	def send(m: Msg): Unit =
		this.queue.add(TrueMsg.inner(m))
	def startListener(): Unit =
		Thread.startVirtualThread(() => this.mainLoop())
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
