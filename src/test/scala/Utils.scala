// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage.SQLManager
import BallCore.UI.{UIProgram, UIServices}
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import munit.Assertions
import natchez.Trace.Implicits.noop
import skunk.implicits.*
import skunk.util.Origin
import skunk.{Fragment, Session, *}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import be.seeseemelk.mockbukkit.MockBukkit

val mockServerSingleton = MockBukkit.mock()

object TestDatabase:
    def setup(opts: munit.TestOptions): SQLManager =
        val session: Resource[IO, Session[IO]] = Session.single(
            host = "localhost",
            port = 5432,
            user = "civcubed",
            database = "civcubed",
            password = Some("shitty password"),
        )
        val cleanName =
            opts.name.replace(" ", "").replace("-", "").replace("'", "")
        val nameFragment =
            Fragment(List(Left(cleanName)), Void.codec, Origin.unknown)
        session
            .use { s =>
                s.execute(sql"""
            CREATE DATABASE $nameFragment;
            """.command)
            }
            .unsafeRunSync()
        val testSession: Resource[IO, Session[IO]] = Session.single(
            host = "localhost",
            port = 5432,
            user = "civcubed",
            database = cleanName,
            password = Some("shitty password"),
        )
        testSession
            .use { s =>
                s.execute(sql"""
            CREATE EXTENSION postgis;
            """.command)
            }
            .unsafeRunSync()
        new SQLManager(testSession, cleanName)

    def teardown(s: SQLManager): Unit =
        val session: Resource[IO, Session[IO]] = Session.single(
            host = "localhost",
            port = 5432,
            user = "civcubed",
            database = "civcubed",
            password = Some("shitty password"),
        )
        val nameFragment =
            Fragment(List(Left(s.database)), Void.codec, Origin.unknown)
        session
            .use { s =>
                s.execute(sql"""
            DROP DATABASE $nameFragment;
            """.command)
            }
            .unsafeRunSync()
        ()

class TestUIServices(assertions: Assertions) extends UIServices:
    val promptQueue: mutable.Queue[(String, Promise[String])] =
        scala.collection.mutable.Queue[(String, Promise[String])]()
    val transferQueue: mutable.Queue[Promise[(UIProgram, Any)]] =
        scala.collection.mutable.Queue[Promise[(UIProgram, Any)]]()
    val notifyQueue: mutable.Queue[Promise[String]] =
        scala.collection.mutable.Queue[Promise[String]]()

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

    override def quit(): Unit =
        ???
