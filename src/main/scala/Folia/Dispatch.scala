// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Folia

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

object Dispatch:
  def apply[T](body: => T)(implicit executor: ExecutionContext): Future[T] =
    val future = Future(body)
    future.onComplete(x =>
      x match
        case Failure(exception) =>
          executor.reportFailure(exception)
        case _ => ()
    )
    future

object FireAndForget:
  def apply[T](body: => T)(implicit executor: ExecutionContext): Unit =
    val _ = Dispatch(body)
