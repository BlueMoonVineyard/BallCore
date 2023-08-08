// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import java.sql.DriverManager
import java.io.File
import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor
import java.sql.SQLException

case class Migration(name: String, apply: List[scalikejdbc.SQL[Any, NoExtractor]], reverse: List[scalikejdbc.SQL[Any, NoExtractor]])
case class MigrationFailure(which: String, num: Int, why: SQLException) extends Exception(s"$which failed at the ${num+1}-th fragment", why)

class SQLManager(test: Option[String] = None):
    File("data-storage").mkdirs()

    Class.forName("org.sqlite.JDBC")
    DriverManager.registerDriver(SQLiteNoReadOnlyDriver)
    if test.isDefined then
        ConnectionPool.add(test.get, "no-read-only:jdbc:sqlite::memory:", null, null)
    else
        ConnectionPool.singleton("no-read-only:jdbc:sqlite:data-storage/BallCore.db", null, null)

    val pool: ConnectionPool = if test.isDefined then ConnectionPool(test.get) else ConnectionPool()
    given session: DBSession = if test.isDefined then NamedAutoSession(test.get) else AutoSession

    sql"PRAGMA foreign_keys=ON".update.apply()
    sql"CREATE TABLE IF NOT EXISTS _Migrations (Name string)".update.apply()

    def localTx[A](execution: DBSession => A) =
        if test.isDefined then
            NamedDB(test.get).localTx(execution)
        else
            DB.localTx(execution)

    // TODO: transactions
    def applyMigration(migration: Migration): Unit =
        localTx { implicit session =>
            val count = sql"SELECT COUNT(*) FROM _Migrations WHERE NAME = ${migration.name};"
                .map(rs => rs.int(1))
                .single
                .apply()
            if count.get == 0 then
                migration.apply.zipWithIndex.foreach { (s, idx) =>
                    try
                        if s.tags.contains("returns") then
                            s.map(_ => ()).list.apply()
                        else
                            s.update.apply()
                    catch
                        case e: SQLException => throw MigrationFailure(migration.name, idx, e)
                }
                sql"INSERT INTO _Migrations (Name) VALUES (${migration.name});".update.apply(); ()
        }
