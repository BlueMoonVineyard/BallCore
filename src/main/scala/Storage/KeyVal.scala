package BallCore.Storage

import java.util.UUID
import scala.reflect.ClassTag

import io.circe._, io.circe.generic.semiauto._

trait KeyVal:
    def set[A](player: UUID, key: String, value: A)(using Encoder[A]): Unit
    def get[A](player: UUID, key: String)(using Decoder[A]): Option[A]
    def remove(player: UUID, key: String): Unit

    def set[A](superkey: String, key: String, value: A)(using Encoder[A]): Unit
    def get[A](superkey: String, key: String)(using Decoder[A]): Option[A]
    def remove(superkey: String, key: String): Unit

// this is how you define encoder/decoder instances at type definition site
// case class KVFloat(value: Float)
// implicit val kvfloatDecoder: Decoder[KVFloat] = deriveDecoder[KVFloat]
// implicit val kvfloatEncoder: Encoder[KVFloat] = deriveEncoder[KVFloat]
