package BallCore.CustomItems

import org.bukkit.block.Block
import io.circe._
import org.bukkit.NamespacedKey

trait BlockManager:
    def store[A](block: Block, key: String, what: A)(using Encoder[A]): Unit
    def retrieve[A](block: Block, key: String)(using Decoder[A]): Option[A]

    def getCustomItem(block: Block)(using registry: ItemRegistry): Option[CustomItem] =
        import NamespacedKeyCodec._

        retrieve[NamespacedKey](block, "item-id").flatMap(registry.lookup)
