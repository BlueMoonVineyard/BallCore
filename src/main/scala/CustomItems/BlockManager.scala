// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import org.bukkit.block.Block
import io.circe._
import org.bukkit.NamespacedKey
import skunk.Session
import cats.effect.IO

trait BlockManager:
    def store[A](block: Block, key: String, what: A)(using Encoder[A], Decoder[A], Session[IO]): IO[Unit]
    def retrieve[A](block: Block, key: String)(using Encoder[A], Decoder[A], Session[IO]): IO[Option[A]]
    def remove(block: Block, key: String)(using Session[IO]): IO[Unit]

    def clearCustomItem(block: Block)(using Session[IO]): IO[Unit] =
        remove(block, "item-id")
    def setCustomItem(block: Block, item: CustomItem)(using Session[IO]): IO[Unit] =
        import NamespacedKeyCodec._

        store(block, "item-id", item.id)
    def getCustomItem(block: Block)(using registry: ItemRegistry)(using Session[IO]): IO[Option[CustomItem]] =
        import NamespacedKeyCodec._

        retrieve[NamespacedKey](block, "item-id").map(_.flatMap(registry.lookup))
