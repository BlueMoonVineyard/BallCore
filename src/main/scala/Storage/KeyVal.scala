package BallCore.Storage

import java.util.UUID
import scala.reflect.ClassTag

trait KeyVal:
    def set(player: UUID, key: String, value: Serializable): Unit
    def get[A <: Serializable](player: UUID, key: String)(using tag: ClassTag[A]): Option[A]
    def remove(player: UUID, key: String): Unit
    
    def set(superkey: String, key: String, value: Serializable): Unit
    def get[A <: Serializable](superkey: String, key: String)(using tag: ClassTag[A]): Option[A]
    def remove(superkey: String, key: String): Unit
    
case class KVFloat(value: Float)
