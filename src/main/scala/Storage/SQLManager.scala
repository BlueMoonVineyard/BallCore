// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import java.sql.DriverManager
import java.io.File
import scala.util.{Try, Using}
import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.sqlite.SQLiteException

case class Migration(name: String, apply: List[scalikejdbc.SQL[Any, NoExtractor]], reverse: List[scalikejdbc.SQL[Any, NoExtractor]])
case class MigrationFailure(which: String, num: Int, why: SQLiteException) extends Exception(s"$which failed at the ${num+1}-th fragment", why)

class SQLManager(test: Boolean = false):
    File("data-storage").mkdirs()

    Class.forName("org.sqlite.JDBC")
    DriverManager.registerDriver(SQLiteNoReadOnlyDriver)
    if test then
        ConnectionPool.singleton("no-read-only:jdbc:sqlite::memory:", null, null)
    else
        ConnectionPool.singleton("no-read-only:jdbc:sqlite:data-storage/BallCore.db", null, null)
    {
        sql"PRAGMA foreign_keys=ON".update.apply()(AutoSession)
        sql"CREATE TABLE IF NOT EXISTS _Migrations (Name string)".update.apply()(AutoSession)
    }

    // TODO: transactions
    def applyMigration(migration: Migration): Unit =
        implicit val session = AutoSession
        DB.localTx { implicit session =>
            val count = sql"SELECT COUNT(*) FROM _Migrations WHERE NAME = ${migration.name};"
                .map(rs => rs.int(1))
                .single
                .apply()
            if count.get == 0 then
                migration.apply.zipWithIndex.foreach { (s, idx) =>
                    try
                        s.update.apply()
                    catch
                        case e: SQLiteException => throw MigrationFailure(migration.name, idx, e)
                }
                sql"INSERT INTO _Migrations (Name) VALUES (${migration.name});".update.apply()
        }
