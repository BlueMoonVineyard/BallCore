// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import org.bukkit.block.Block
import io.circe._
import org.bukkit.NamespacedKey

trait BlockManager:
    def store[A](block: Block, key: String, what: A)(using Encoder[A]): Unit
    def retrieve[A](block: Block, key: String)(using Decoder[A]): Option[A]
    def remove(block: Block, key: String): Unit

    def clearCustomItem(block: Block): Unit =
        remove(block, "item-id")
    def setCustomItem(block: Block, item: CustomItem): Unit =
        import NamespacedKeyCodec._

        store(block, "item-id", item.id)
    def getCustomItem(block: Block)(using registry: ItemRegistry): Option[CustomItem] =
        import NamespacedKeyCodec._

        retrieve[NamespacedKey](block, "item-id").flatMap(registry.lookup)
