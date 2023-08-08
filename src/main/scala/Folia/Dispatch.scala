package BallCore.Folia

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure

object Dispatch:
	def apply[T](body: => T)(implicit executor: ExecutionContext): Future[T] =
		val future = Future(body)
		future.onComplete(x => x match
			case Failure(exception) =>
				executor.reportFailure(exception)
			case _ => ()
		)
		future

object FireAndForget:
	def apply[T](body: => T)(implicit executor: ExecutionContext): Unit =
		val _ = Dispatch(body)
