// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import io.circe.{Decoder, Encoder}
import org.bukkit.NamespacedKey

object NamespacedKeyCodec:
  implicit val encoder: Encoder[NamespacedKey] = Encoder
    .encodeTuple2[String, String]
    .contramap[NamespacedKey](k => (k.getNamespace(), k.getKey()))
  implicit val decoder: Decoder[NamespacedKey] =
    Decoder.decodeTuple2[String, String].map((n, k) => NamespacedKey(n, k))
