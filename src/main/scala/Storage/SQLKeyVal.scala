// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import java.util.UUID
import scalikejdbc._
import io.circe._, io.circe.parser._, io.circe.syntax._

class SQLKeyVal()(using sql: SQLManager) extends KeyVal:
    sql.applyMigration(
        Migration(
            "Initial SQLKeyVal",
            List(
                sql"""
                CREATE TABLE PlayerKeyValue (
                    Player string,
                    Key string,
                    Value string,
                    UNIQUE(Player, Key)
                );
                """,
                sql"""
                CREATE TABLE GlobalKeyValue (
                    Superkey string,
                    Key string,
                    Value string,
                    UNIQUE(Superkey, Key)
                );
                """
            ),
            List(
                sql"""
                DROP TABLE GlobalKeyValue;
                """,
                sql"""
                DROP TABLE PlayerKeyValue;
                """
            )
        )
    )

    implicit val session: DBSession = sql.session

    override def set[A](player: UUID, key: String, value: A)(using Encoder[A]): Unit =
        sql"INSERT OR REPLACE INTO PlayerKeyValue (Player, Key, Value) VALUES (${player}, ${key}, ${value.asJson.noSpaces})".update.apply(); ()
    override def get[A](player: UUID, key: String)(using Decoder[A]): Option[A] =
        sql"SELECT Value from PlayerKeyValue WHERE Player = ${player} AND Key = ${key}"
            .map(rs => rs.string(1))
            .single
            .apply()
            .flatMap(x => decode[A](x).toOption)
    override def remove(player: UUID, key: String): Unit =
        sql"DELETE FROM PlayerKeyValue WHERE Player = ${player} AND Key = ${key}".update.apply(); ()
    override def set[A](superkey: String, key: String, value: A)(using Encoder[A]): Unit =
        sql"INSERT OR REPLACE INTO GlobalKeyValue (Superkey, Key, Value) VALUES (${superkey}, ${key}, ${value.asJson.noSpaces})".update.apply(); ()
    override def get[A](superkey: String, key: String)(using Decoder[A]): Option[A] =
        sql"SELECT Value from GlobalKeyValue WHERE Superkey = ${superkey} AND Key = ${key}"
            .map(rs => rs.string(1))
            .single
            .apply()
            .flatMap(x => decode[A](x).toOption)
    override def remove(superkey: String, key: String): Unit =
        sql"DELETE FROM GlobalKeyValue WHERE Superkey = ${superkey} AND Key = ${key}".update.apply(); ()
