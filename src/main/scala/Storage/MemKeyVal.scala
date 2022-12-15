package BallCore.Storage

import scala.collection.mutable.Map
import java.{util => ju}
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

class MemKeyVal extends KeyVal:
    private val globalKeys: Map[(String, String), String] = Map()
    private val playerKeys: Map[(ju.UUID, String), String] = Map()

    def debugDump(): Unit =
        println(globalKeys)
        println(playerKeys)

    override def set[A](superkey: String, key: String, value: A)(using Encoder[A]): Unit =
        globalKeys((superkey, key)) = value.asJson.noSpaces
    override def set[A](player: ju.UUID, key: String, value: A)(using Encoder[A]): Unit =
        playerKeys((player, key)) = value.asJson.noSpaces
    override def get[A](superkey: String, key: String)(using Decoder[A]): Option[A] =
        globalKeys.get((superkey, key)).flatMap(x => decode[A](x).toOption)
    override def get[A](player: ju.UUID, key: String)(using Decoder[A]): Option[A] =
        playerKeys.get((player, key)).flatMap(x => decode[A](x).toOption)
    override def remove(superkey: String, key: String): Unit =
        globalKeys.remove((superkey, key))
    override def remove(player: ju.UUID, key: String): Unit =
        playerKeys.remove((player, key))
