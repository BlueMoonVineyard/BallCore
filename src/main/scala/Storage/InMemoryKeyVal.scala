package BallCore.Storage

import java.util.UUID
import scala.reflect.ClassTag
import scala.collection.mutable.Map
import java.{util => ju}

class InMemoryKeyVal extends KeyVal:
    var items: Map[(UUID, String), Any] = Map()
    var globalItems: Map[(String, String), Any] = Map()

    override def set(player: ju.UUID, key: String, value: Serializable): Unit =
        items((player, key)) = value
    override def get[A <: Serializable](player: ju.UUID, key: String)(using tag: ClassTag[A]): Option[A] =
        if !items.contains((player, key)) then
            None
        else
            Some( items((player, key)).asInstanceOf[A] )
    override def remove(player: ju.UUID, key: String): Unit =
        items.remove((player, key))

    override def set(superkey: String, key: String, value: Serializable): Unit =
        globalItems((superkey, key)) = value

    override def get[A <: Serializable](superkey: String, key: String)(using tag: ClassTag[A]): Option[A] =
        if !globalItems.contains((superkey, key)) then
            None
        else
            Some( globalItems((superkey, key)).asInstanceOf[A] )
    override def remove(superkey: String, key: String): Unit =
        globalItems.remove((superkey, key))