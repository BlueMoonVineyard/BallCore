// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import cats.effect.IO
import io.circe.*
import io.circe.syntax.*
import skunk.Session
import skunk.circe.codec.all.*
import skunk.codec.all.*
import skunk.implicits.*

import java.util.UUID

class SQLKeyVal()(using sql: SQLManager) extends KeyVal:
  sql.applyMigration(
    Migration(
      "Initial SQLKeyVal",
      List(
        sql"""
                CREATE TABLE PlayerKeyValue (
                    Player UUID,
                    Key TEXT,
                    Value JSONB,
                    UNIQUE(Player, Key)
                );
                """.command,
        sql"""
                CREATE TABLE GlobalKeyValue (
                    Superkey TEXT,
                    Key TEXT,
                    Value JSONB,
                    UNIQUE(Superkey, Key)
                );
                """.command
      ),
      List(
        sql"""
                DROP TABLE GlobalKeyValue;
                """.command,
        sql"""
                DROP TABLE PlayerKeyValue;
                """.command
      )
    )
  )

  override def set[A](player: UUID, key: String, value: A)(using
      Encoder[A],
      Decoder[A],
      Session[IO]
  ): IO[Unit] =
    sql
      .commandIO(
        sql"""
        INSERT INTO PlayerKeyValue (Player, Key, Value) VALUES ($uuid, $text, $jsonb) ON CONFLICT (Player, Key) DO UPDATE SET Value = EXCLUDED.Value;
        """,
        (player, key, value.asJson)
      )
      .map(_ => ())

  override def get[A](player: UUID, key: String)(using
      Encoder[A],
      Decoder[A],
      Session[IO]
  ): IO[Option[A]] =
    sql.queryOptionIO(
      sql"""
        SELECT Value FROM PlayerKeyValue WHERE Player = $uuid AND KEY = $text
        """,
      jsonb,
      (player, key)
    )

  override def remove(player: UUID, key: String)(using Session[IO]): IO[Unit] =
    sql
      .commandIO(
        sql"""
        DELETE FROM PlayerKeyValue WHERE Player = $uuid AND Key = $text
        """,
        (player, key)
      )
      .map(_ => ())

  override def set[A](superkey: String, key: String, value: A)(using
      Encoder[A],
      Decoder[A],
      Session[IO]
  ): IO[Unit] =
    sql
      .commandIO(
        sql"""
        INSERT INTO GlobalKeyValue (Superkey, Key, Value) VALUES ($text, $text, $jsonb) ON CONFLICT (Superkey, Key) DO UPDATE SET Value = EXCLUDED.Value;
        """,
        (superkey, key, value.asJson)
      )
      .map(_ => ())

  override def get[A](superkey: String, key: String)(using
      Encoder[A],
      Decoder[A],
      Session[IO]
  ): IO[Option[A]] =
    sql.queryOptionIO(
      sql"""
        SELECT Value FROM GlobalKeyValue WHERE Superkey = $text AND KEY = $text
        """,
      jsonb,
      (superkey, key)
    )

  override def remove(superkey: String, key: String)(using
      Session[IO]
  ): IO[Unit] =
    sql
      .commandIO(
        sql"""
        DELETE FROM GlobalKeyValue WHERE Superkey = $text AND KEY = $text
        """,
        (superkey, key)
      )
      .map(_ => ())
