// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import cats.effect.IO
import io.circe.*
import skunk.Session

import java.util.UUID

trait KeyVal:
  def set[A](player: UUID, key: String, value: A)(using
                                                  Encoder[A],
                                                  Decoder[A],
                                                  Session[IO]
  ): IO[Unit]

  def get[A](player: UUID, key: String)(using
                                        Encoder[A],
                                        Decoder[A],
                                        Session[IO]
  ): IO[Option[A]]

  def remove(player: UUID, key: String)(using Session[IO]): IO[Unit]

  def set[A](superkey: String, key: String, value: A)(using
                                                      Encoder[A],
                                                      Decoder[A],
                                                      Session[IO]
  ): IO[Unit]

  def get[A](superkey: String, key: String)(using
                                            Encoder[A],
                                            Decoder[A],
                                            Session[IO]
  ): IO[Option[A]]

  def remove(superkey: String, key: String)(using Session[IO]): IO[Unit]

// this is how you define encoder/decoder instances at type definition site
// case class KVFloat(value: Float)
// implicit val kvfloatDecoder: Decoder[KVFloat] = deriveDecoder[KVFloat]
// implicit val kvfloatEncoder: Encoder[KVFloat] = deriveEncoder[KVFloat]
