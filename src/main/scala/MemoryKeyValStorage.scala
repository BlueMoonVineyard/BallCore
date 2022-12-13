package BallCore

import java.util.UUID
import scala.reflect.ClassTag
import scala.collection.mutable.Map
import java.{util => ju}

class MemoryKeyValStorage extends KeyValStorage {
    var items: Map[(UUID, String), Any] = Map()

    override def set(player: ju.UUID, key: String, value: Serializable): Unit = {
        items((player, key)) = value
    }

    override def get[A <: Serializable](player: ju.UUID, key: String)(using tag: ClassTag[A]): Option[A] = {
        if items.contains((player, key)) then
            None
        else
            Some( items((player, key)).asInstanceOf[A] )
    }
}
