// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import BallCore.Storage.KeyVal
import cats.effect.IO
import io.circe.*
import org.bukkit.block.Block
import skunk.Session

class KeyValBlockManager(using kv: KeyVal) extends BlockManager:
  private def keyof(b: Block, key: String): String =
    s"${b.getWorld.getUID}|${b.getX}|${b.getY}|${b.getZ}|$key"

  def store[A](block: Block, key: String, what: A)(using
      Encoder[A],
      Decoder[A],
      Session[IO]
  ): IO[Unit] =
    kv.set("blockdb", keyof(block, key), what)

  def retrieve[A](block: Block, key: String)(using
      Encoder[A],
      Decoder[A],
      Session[IO]
  ): IO[Option[A]] =
    kv.get("blockdb", keyof(block, key))

  def remove(block: Block, key: String)(using Session[IO]): IO[Unit] =
    kv.remove("blockdb", keyof(block, key))
