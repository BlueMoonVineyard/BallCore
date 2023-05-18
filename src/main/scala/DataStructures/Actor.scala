package BallCore.DataStructures

import java.util.concurrent.LinkedTransferQueue

trait Actor[Msg]:
	def handle(m: Msg): Unit

	private val queue = LinkedTransferQueue[Msg]()

	def send(m: Msg): Unit =
		this.queue.add(m)
	def startListener(): Unit =
		Thread.startVirtualThread(() => this.mainLoop())
	private def mainLoop(): Unit =
		while true do
			val msg = this.queue.take()
			try
				handle(msg)
			catch
				case e: Exception => e.printStackTrace()
