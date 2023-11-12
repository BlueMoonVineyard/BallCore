// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import skunk.Session

import java.util as ju
import scala.collection.mutable
import scala.collection.mutable.Map

class MemKeyVal extends KeyVal:
  private val globalKeys: mutable.Map[(String, String), String] = mutable.Map()
  private val playerKeys: mutable.Map[(ju.UUID, String), String] = mutable.Map()

  def debugDump(): Unit =
    println(globalKeys)
    println(playerKeys)

  override def set[A](superkey: String, key: String, value: A)(using
                                                               Encoder[A],
                                                               Decoder[A],
                                                               Session[IO]
  ): IO[Unit] =
    IO(globalKeys((superkey, key)) = value.asJson.noSpaces)

  override def set[A](player: ju.UUID, key: String, value: A)(using
                                                              Encoder[A],
                                                              Decoder[A],
                                                              Session[IO]
  ): IO[Unit] =
    IO(playerKeys((player, key)) = value.asJson.noSpaces)

  override def get[A](superkey: String, key: String)(using
                                                     Encoder[A],
                                                     Decoder[A],
                                                     Session[IO]
  ): IO[Option[A]] =
    IO(globalKeys.get((superkey, key)).flatMap(x => decode[A](x).toOption))

  override def get[A](player: ju.UUID, key: String)(using
                                                    Encoder[A],
                                                    Decoder[A],
                                                    Session[IO]
  ): IO[Option[A]] =
    IO(playerKeys.get((player, key)).flatMap(x => decode[A](x).toOption))

  override def remove(superkey: String, key: String)(using
                                                     Session[IO]
  ): IO[Unit] =
    IO {
      globalKeys.remove((superkey, key))
      ()
    }

  override def remove(player: ju.UUID, key: String)(using
                                                    Session[IO]
  ): IO[Unit] =
    IO {
      playerKeys.remove((player, key))
      ()
    }
