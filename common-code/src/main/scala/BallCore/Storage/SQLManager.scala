// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import cats.effect.*
import cats.effect.cps.*
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import cats.syntax.traverse.*
import natchez.Trace.Implicits.noop
import org.spongepowered.configurate.CommentedConfigurationNode
import skunk.*
import skunk.codec.all.*
import skunk.data.Completion
import skunk.implicits.*
import skunk.util.Origin

import java.sql.SQLException
import scala.concurrent.Future
import scala.util.Try
import scala.util.chaining.*

case class Migration(
    name: String,
    apply: List[Command[Void]],
    reverse: List[Command[Void]],
)

case class MigrationFailure(which: String, num: Int, why: SQLException)
    extends Exception(s"$which failed at the ${num + 1}-th fragment", why)

case class Config(
    host: String,
    port: Int,
    user: String,
    database: String,
    password: String,
)

enum ConfigError:
    case missingHost
    case missingPort
    case missingUser
    case missingDatabase
    case missingPassword
    case error(of: Throwable)

object Config:
    private def get[V](
        config: CommentedConfigurationNode,
        node: String,
        klass: Class[V],
        left: ConfigError,
    ): Either[ConfigError, V] =
        for {
            valueOpt <- Try(Option(config.node(node).get(klass))).toEither.left
                .map(ConfigError.error.apply)
            value <- valueOpt.toRight(left)
        } yield value

    def from(config: CommentedConfigurationNode): Either[ConfigError, Config] =
        import ConfigError.*

        for {
            host <- get(config, "host", classOf[String], missingHost)
            port <- get(config, "port", classOf[Integer], missingPort)
            user <- get(config, "user", classOf[String], missingUser)
            data <- get(config, "database", classOf[String], missingDatabase)
            pass <- get(config, "password", classOf[String], missingPassword)
        } yield Config(host, port, user, data, pass)

object SQLManager:
    def apply(config: Config): (SQLManager, IO[Unit]) =
        val pool = Session.pooled[IO](
            host = config.host,
            port = config.port,
            user = config.user,
            database = config.database,
            max = 5,
            password = Some(config.password),
        )
        val launcher: IO[(Resource[IO, Session[IO]], IO[Unit])] = pool.allocated
        val (resource, shutdownHook) = launcher.unsafeRunSync()
        (new SQLManager(resource, "civcubed"), shutdownHook)

class SQLManager(resource: Resource[IO, Session[IO]], val database: String):
    val session: Resource[IO, Session[IO]] = resource

    val runtime: IORuntime = global

    private val countQuery: Query[String, Long] =
        sql"""
        SELECT COUNT(*) FROM _Migrations WHERE NAME = $text
        """.query(int8)

    private val insertCommand: Command[String] =
        sql"""
        INSERT INTO _Migrations (Name) VALUES ($text)
        """.command

    session
        .use { s =>
            sql"""
        CREATE TABLE IF NOT EXISTS _Migrations (
            Name TEXT PRIMARY KEY
        );
        """.command.pipe(s.execute)
        }
        .unsafeRunSync()

    /** Applies a migration synchronously
      *
      * Migrations are ran in a transaction.
      *
      * @param migrationP
      *   The migration to run the `apply` queries of
      */
    def applyMigration(migrationP: Migration): Unit =
        val migration = migrationP
        session
            .use { s =>
                s.transaction.use { tx =>
                    async[IO] {
                        val prepared = s.prepare(countQuery).await
                        val count = prepared.unique(migration.name).await
                        if count == 0 then
                            migration.apply.traverse(s.execute).await
                            s.execute(insertCommand)(migration.name).await
                    }
                }
            }
            .unsafeRunSync()

    /** Runs IO code in the context of a SQL transaction
      *
      * All SQL code ran within the transaction will be part of it
      *
      * Will roll back if an exception is thrown
      *
      * @param fn
      *   the function to run within the transaction
      * @tparam A
      *   return type of the IO function to be ran within the transaction
      */
    def txIO[A](fn: Transaction[IO] => IO[A])(using s: Session[IO]): IO[A] =
        s.transaction.use { tx =>
            fn(tx)
        }

    def withTX[A](fn: Transaction[IO] ?=> IO[A])(using s: Session[IO]): IO[A] =
        s.transaction.use { tx =>
            fn(using tx)
        }

    /** Runs IO code that requires a database connection
      *
      * @param fn
      *   the IO code to execute that requires a [[skunk.Session]]
      */
    def useIO[T](fn: Session[IO] ?=> IO[T]): IO[T] =
        session.use { implicit session => fn }

    /** Runs IO code that requires a database connection and returns a
      * [[scala.concurrent.Future]]
      *
      * @see
      *   useIO
      */
    def useFuture[T](fn: Session[IO] ?=> IO[T]): Future[T] =
        session.use { implicit session => fn }.unsafeToFuture()

    /** Runs IO code that requires a database connection synchronously
      *
      * @see
      *   useIO
      */
    def useBlocking[T](fn: Session[IO] ?=> IO[T]): T =
        session.use { implicit session => fn }.unsafeRunSync()

    /** Runs IO code that requires a database connection asynchronously without
      * returning any values
      *
      * @see
      *   useIO
      */
    def useFireAndForget[T](fn: Session[IO] ?=> IO[T]): Unit =
        session.use { implicit session => fn }.unsafeRunAndForget()

    /** Executes a valueless SQL query with the given parameters
      *
      * @example
      *   {{{ sql.commandIO(sql""" INSERT INTO SomeDatabase (Hello, World)
      *   VALUES ($int4, $uuid) """, (someInt, someUUID)) }}}
      * @see
      *   useIO
      */
    def commandIO[T](fragmentP: Fragment[T], parameters: T)(using
        s: Session[IO]
    ): IO[Completion] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.command).await
            val res = prepared.execute(parameters).await
            res
        }

    /** Executes a SQL query with the given parameters that returns exactly one
      * value
      *
      * @example
      *   {{{ sql.queryUniqueIO(sql""" SELECT (Hello, World) FROM SomeDatabase
      *   WHERE Foo = $int4; """, (int4 *: uuid), someInt) }}}
      * @see
      *   useIO
      */
    def queryUniqueIO[I, O](
        fragmentP: Fragment[I],
        decoder: Decoder[O],
        parameters: I,
    )(using s: Session[IO]): IO[O] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.query(decoder)).await
            val res = prepared.unique(parameters).await
            res
        }

    /** Executes a SQL query with the given parameters that returns 0 or more
      * values
      *
      * @example
      *   {{{ sql.queryListIO(sql"""SELECT (Hello, World) FROM SomeDatabase
      *   WHERE Foo = $int4; """, (int4 *: uuid), someInt) }}}
      * @see
      *   useIO
      */
    def queryListIO[I, O](
        fragmentP: Fragment[I],
        decoder: Decoder[O],
        parameters: I,
    )(using s: Session[IO]): IO[List[O]] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.query(decoder)).await
            val res = prepared.stream(parameters, 64).compile.toList.await
            res
        }

    /** Executes a SQL query with the given parameters that returns 0 or 1
      * values
      *
      * @example
      *   {{{ sql.queryOptionIO(sql""" SELECT (Hello, World) FROM SomeDatabase
      *   WHERE Foo = $int4; """, (int4 *: uuid), someInt) }}}
      * @see
      *   useIO
      */
    def queryOptionIO[I, O](
        fragmentP: Fragment[I],
        decoder: Decoder[O],
        parameters: I,
    )(using s: Session[IO]): IO[Option[O]] =
        val fragment = fragmentP
        async[IO] {
            val prepared = s.prepare(fragment.query(decoder)).await
            val res = prepared.option(parameters).await
            res
        }
