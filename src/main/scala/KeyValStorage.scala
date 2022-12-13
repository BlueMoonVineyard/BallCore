package BallCore

import java.util.UUID
import scala.reflect.ClassTag

trait KeyValStorage {
    def set(player: UUID, key: String, value: Serializable): Unit
    def get[A <: Serializable](player: UUID, key: String)(using tag: ClassTag[A]): Option[A]
}

case class KVFloat(value: Float)
