package BallCore.Storage

import java.util.UUID
import scala.reflect.ClassTag

trait KeyVal:
    def set(player: UUID, key: String, value: Serializable): Unit
    def get[A <: Serializable](player: UUID, key: String)(using tag: ClassTag[A]): Option[A]

case class KVFloat(value: Float)
