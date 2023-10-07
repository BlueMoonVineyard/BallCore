// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import java.sql.SQLException
import cats.effect._
import cats.effect.unsafe.implicits.global
import skunk._
import skunk.implicits._
import natchez.Trace.Implicits.noop
import scala.util.chaining._
import skunk.codec.all._
import cats.effect.cps._
import scala.concurrent.Future
import skunk.data.Completion
import skunk.util.Origin
import cats.syntax.traverse._

case class Migration(name: String, apply: List[Command[Void]], reverse: List[Command[Void]])
case class MigrationFailure(which: String, num: Int, why: SQLException) extends Exception(s"$which failed at the ${num+1}-th fragment", why)

object SQLManager:
    def apply(): SQLManager =
        val session: Resource[IO, Session[IO]] = Session.single(
            host = "localhost",
            port = 5432,
            user = "civcubed",
            database = "civcubed",
            password = Some("civcubed")
        )
        new SQLManager(session, "civcubed")

class SQLManager(resource: Resource[IO, Session[IO]], val database: String):
    val session: Resource[IO, Session[IO]] = resource

    val runtime = global

    val countQuery: Query[String, Long] =
        sql"""
        SELECT COUNT(*) FROM _Migrations WHERE NAME = $text
        """.query(int8)

    val insertCommand: Command[String] =
        sql"""
        INSERT INTO _Migrations (Name) VALUES ($text)
        """.command

    session.use { s =>
        sql"""
        CREATE TABLE IF NOT EXISTS _Migrations (
            Name TEXT PRIMARY KEY
        );
        """.command.pipe(s.execute)
    }.unsafeRunSync()

    // TODO: transactions
    def applyMigration(migrationP: Migration): Unit =
        val migration = migrationP
        session.use { s =>
            s.transaction.use { tx =>
                async[IO] {
                    val prepared = s.prepare(countQuery).await
                    val count = prepared.unique(migration.name).await
                    if count == 0 then
                        migration.apply.traverse(s.execute).await
                        s.execute(insertCommand)(migration.name).await
                }
            }
        }.unsafeRunSync()

    def txIO[A](fn: (Transaction[IO]) => IO[A])(using s: Session[IO]): IO[A] =
        s.transaction.use { tx =>
            fn(tx)
        }

    def useIO[T](fn: Session[IO] ?=> IO[T]): IO[T] =
        session.use { implicit session => fn }

    def useFuture[T](fn: Session[IO] ?=> IO[T]): Future[T] =
        session.use { implicit session => fn }.unsafeToFuture()

    def useBlocking[T](fn: Session[IO] ?=> IO[T]): T =
        session.use { implicit session => fn }.unsafeRunSync()

    def useFireAndForget[T](fn: Session[IO] ?=> IO[T]): Unit =
        session.use { implicit session => fn }.unsafeRunAndForget()

    def commandIO[T](fragmentP: Fragment[T], parameters: T)(using s: Session[IO]): IO[Completion] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.command).await
            val res = prepared.execute(parameters).await
            res
        }

    def queryUniqueIO[I, O](fragmentP: Fragment[I], decoder: Decoder[O], parameters: I)(using s: Session[IO]): IO[O] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.query(decoder)).await
            val res = prepared.unique(parameters).await
            res
        }

    def command[T](fragmentP: Fragment[T], parameters: T): Future[Completion] =
        useIO(commandIO(fragmentP, parameters)).unsafeToFuture()

    def queryListIO[I, O](fragmentP: Fragment[I], decoder: Decoder[O], parameters: I)(using s: Session[IO]): IO[List[O]] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.query(decoder)).await
            val res = prepared.stream(parameters, 64).compile.toList.await
            res
        }

    def queryList[I, O](fragmentP: Fragment[I], decoder: Decoder[O], parameters: I): Future[List[O]] =
        useIO(queryListIO(fragmentP, decoder, parameters)).unsafeToFuture()

    def queryOptionIO[I, O](fragmentP: Fragment[I], decoder: Decoder[O], parameters: I)(using s: Session[IO]): IO[Option[O]] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.query(decoder)).await
            val res = prepared.option(parameters).await
            res
        }

    def queryOption[I, O](fragmentP: Fragment[I], decoder: Decoder[O], parameters: I): Future[Option[O]] =
        useIO(queryOptionIO(fragmentP, decoder, parameters)).unsafeToFuture()
