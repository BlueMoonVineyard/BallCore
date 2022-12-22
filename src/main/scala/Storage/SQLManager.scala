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

case class Migration(name: String, apply: List[scalikejdbc.SQL[Any, NoExtractor]], reverse: List[scalikejdbc.SQL[Any, NoExtractor]])

class SQLManager:
    File("data-storage").mkdirs()

    Class.forName("org.sqlite.JDBC")
    DriverManager.registerDriver(SQLiteNoReadOnlyDriver)
    ConnectionPool.singleton("no-read-only:jdbc:sqlite:data-storage/BallCore.db", null, null)
    {
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
                migration.apply.foreach { s => s.update.apply() }
                sql"INSERT INTO _Migrations (Name) VALUES (${migration.name});".update.apply()
        }
