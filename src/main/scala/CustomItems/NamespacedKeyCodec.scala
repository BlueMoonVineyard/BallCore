package BallCore.CustomItems

import io.circe.Encoder
import org.bukkit.NamespacedKey
import io.circe.Decoder

object NamespacedKeyCodec:
    implicit val encoder: Encoder[NamespacedKey] = Encoder.encodeTuple2[String, String].contramap[NamespacedKey](k => (k.getNamespace(), k.getKey()))
    implicit val decoder: Decoder[NamespacedKey] = Decoder.decodeTuple2[String, String].map((n, k) => NamespacedKey(n, k))
