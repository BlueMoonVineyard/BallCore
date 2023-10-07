// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Storage

import scala.collection.mutable.Map
import java.{util => ju}
import io.circe._, io.circe.parser._, io.circe.syntax._
import skunk.Session
import cats.effect.IO

class MemKeyVal extends KeyVal:
    private val globalKeys: Map[(String, String), String] = Map()
    private val playerKeys: Map[(ju.UUID, String), String] = Map()

    def debugDump(): Unit =
        println(globalKeys)
        println(playerKeys)

    override def set[A](superkey: String, key: String, value: A)(using Encoder[A], Decoder[A], Session[IO]): IO[Unit] =
        IO(globalKeys((superkey, key)) = value.asJson.noSpaces)
    override def set[A](player: ju.UUID, key: String, value: A)(using Encoder[A], Decoder[A], Session[IO]): IO[Unit] =
        IO(playerKeys((player, key)) = value.asJson.noSpaces)
    override def get[A](superkey: String, key: String)(using Encoder[A], Decoder[A], Session[IO]): IO[Option[A]] =
        IO(globalKeys.get((superkey, key)).flatMap(x => decode[A](x).toOption))
    override def get[A](player: ju.UUID, key: String)(using Encoder[A], Decoder[A], Session[IO]): IO[Option[A]] =
        IO(playerKeys.get((player, key)).flatMap(x => decode[A](x).toOption))
    override def remove(superkey: String, key: String)(using Session[IO]): IO[Unit] =
        IO {
            globalKeys.remove((superkey, key)); ()
        }
    override def remove(player: ju.UUID, key: String)(using Session[IO]): IO[Unit] =
        IO {
            playerKeys.remove((player, key)); ()
        }
