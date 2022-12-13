package BallCore.Storage

import java.util.UUID
import scala.reflect.ClassTag
import java.io.File
import org.bukkit.configuration.file.YamlConfiguration
import scala.collection.mutable.Map
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.StringBufferInputStream
import java.io.ObjectInputStream
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8
import java.io.ByteArrayInputStream

class ConfigKeyVal extends KeyVal:
    val PathPlayers = "data-storage/BallCore/Players/"
    val PathGlobal = "data-storage/BallCore/Global/"
    File(PathPlayers).mkdirs()
    File(PathGlobal).mkdirs()

    val GlobalConfigsDirty: Map[String, Boolean] = Map().withDefault(k => false)
    val GlobalConfigs: Map[String, YamlConfiguration] = Map().withDefault { key =>
            val f = File(PathGlobal + key + ".yaml")
            val conf = if f.exists() then
                YamlConfiguration.loadConfiguration(f)
            else
                YamlConfiguration()
            GlobalConfigs(key) = conf
            conf
        }
    val PlayerConfigsDirty: Map[UUID, Boolean] = Map().withDefault(k => false)
    val PlayerConfigs: Map[UUID, YamlConfiguration] = Map().withDefault { key =>
            val f = File(PathPlayers + key.toString() + ".yaml")
            val conf = if f.exists() then
                YamlConfiguration.loadConfiguration(f)
            else
                YamlConfiguration()
            PlayerConfigs(key) = conf
            conf
        }

    def save() =
        PlayerConfigsDirty.foreach { (k, v) =>
            if v then
                PlayerConfigs(k).save(PathPlayers + k.toString() + ".yaml")
        }
        GlobalConfigsDirty.foreach { (k, v) =>
            if v then
                GlobalConfigs(k).save(PathGlobal + k + ".yaml")
        }
        PlayerConfigsDirty.clear()
        GlobalConfigsDirty.clear()

    def serialise(value: Any): String =
        val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
        val oos = new ObjectOutputStream(stream)
        oos.writeObject(value)
        oos.close
        new String(
            Base64.getEncoder().encode(stream.toByteArray),
            UTF_8
        )

    def deserialise(str: String): Any =
        val bytes = Base64.getDecoder().decode(str.getBytes(UTF_8))
        val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
        val value = ois.readObject
        ois.close
        value

    override def set(player: UUID, key: String, value: Serializable): Unit =
        PlayerConfigsDirty(player) = true
        PlayerConfigs(player).set(key, serialise(value))
    override def get[A <: Serializable](player: UUID, key: String)(using tag: ClassTag[A]): Option[A] =
        if !PlayerConfigs(player).contains(key) then
            None
        else
            Some(deserialise(PlayerConfigs(player).get(key).asInstanceOf[String]).asInstanceOf[A])
    override def remove(player: UUID, key: String): Unit =
        PlayerConfigsDirty(player) = true
        PlayerConfigs(player).set(key, null)

    override def set(superkey: String, key: String, value: Serializable): Unit =
        GlobalConfigsDirty(superkey) = true
        GlobalConfigs(superkey).set(key, serialise(value))
    override def get[A <: Serializable](superkey: String, key: String)(using tag: ClassTag[A]): Option[A] =
        if !GlobalConfigs(superkey).contains(key) then
            None
        else
            Some(deserialise(GlobalConfigs(superkey).get(key).asInstanceOf[String]).asInstanceOf[A])
    override def remove(superkey: String, key: String): Unit =
        GlobalConfigsDirty(superkey) = true
        GlobalConfigs(superkey).set(key, null)