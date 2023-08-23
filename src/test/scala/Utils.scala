// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.UI.{UIProgram, UIServices}
import scala.concurrent.{Future, Promise, ExecutionContext}
import munit.Assertions

class TestUIServices(assertions: Assertions) extends UIServices:
    val promptQueue = scala.collection.mutable.Queue[(String, Promise[String])]()
    val transferQueue = scala.collection.mutable.Queue[Promise[(UIProgram, Any)]]()
    val notifyQueue = scala.collection.mutable.Queue[Promise[String]]()

    def expectTransfer(): Future[(UIProgram, Any)] =
        val p = Promise[(UIProgram, Any)]()
        transferQueue.enqueue(p)
        p.future
    def expectPrompt(answer: String): Future[String] =
        val p = Promise[String]()
        promptQueue.enqueue((answer, p))
        p.future
    def expectNotify(): Future[String] =
        val p = Promise[String]()
        notifyQueue.enqueue(p)
        p.future
    override def transferTo(pr: UIProgram, f: pr.Flags): Unit =
        if transferQueue.isEmpty then
            assertions.assert(false, "program transferred when unexpected")
        else
            val p = transferQueue.dequeue()
            p.success((pr, f))
    override def prompt(prompt: String): Future[String] =
        if promptQueue.isEmpty then
            assertions.assert(false, "program prompted when unexpected")
            throw AssertionError()
        else
            val (ans, wha) = promptQueue.dequeue()
            wha.success(prompt)
            Future.successful(ans)
    override def execute(runnable: Runnable): Unit =
        ExecutionContext.global.execute(runnable)
    override def reportFailure(cause: Throwable): Unit =
        ExecutionContext.global.reportFailure(cause)
    override def notify(what: String): Unit =
        if notifyQueue.isEmpty then
            assertions.assert(false, "program notified when unexpected")
            throw AssertionError()
        else
            val wha = notifyQueue.dequeue()
            wha.success(what)