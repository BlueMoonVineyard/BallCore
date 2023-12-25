package BallCore.PluginMessaging

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import io.circe.generic.semiauto._
import io.circe.Encoder
import BallCore.CraftingStations.CraftingStation
import io.circe.syntax._
import io.circe._
import java.nio.charset.StandardCharsets
import BallCore.CustomItems.ItemRegistry
import org.bukkit.event.player.PlayerJoinEvent

enum RecipeMessage:
    case customItem(item: ItemStack, id: NamespacedKey)
    case newWorkstation(workstation: ItemStack, id: NamespacedKey)
    case allDone()

object RecipeMessage:
    given Encoder[ItemStack] =
        new Encoder[ItemStack]:
            override def apply(a: ItemStack): Json =
                Option(a).map(_.serializeAsBytes()).asJson
    given Encoder[NamespacedKey] =
        new Encoder[NamespacedKey]:
            override def apply(a: NamespacedKey): Json =
                a.toString.asJson

    given Encoder[RecipeMessage] = deriveEncoder[RecipeMessage]

class JoinListener(using p: Plugin, s: List[CraftingStation], ir: ItemRegistry) extends Listener:
    extension (j: Json)
        def encodeToBytes: Array[Byte] =
            val byted = j.noSpaces.toString.getBytes(StandardCharsets.UTF_8)
            val stream = java.io.ByteArrayOutputStream()
            var size = byted.length
            while (size & -128) != 0 do
                stream.write(size & 127 | 128)
                size >>>= 7
            stream.write(size)
            stream.write(byted, 0, byted.length)
            stream.toByteArray
        
    extension (plr: Player)
        def sendPluginMessage(ba: RecipeMessage): Unit =
            val jsonned = ba.asJson
            plr.sendPluginMessage(
                p,
                Messaging.recipes,
                jsonned.encodeToBytes,
            )

    @EventHandler
    def onJoin(event: PlayerJoinEvent): Unit =
        val player = event.getPlayer()
        val field = player.getClass.getDeclaredField("channels")
        field.setAccessible(true)
        val channels = field.get(player).asInstanceOf[java.util.Set[String]]
        channels.add("civcubed:recipes")
        ir.items().foreach { item =>
            player.sendPluginMessage(
                RecipeMessage.customItem(item.template, item.id)
            )
        }
        s.foreach { station =>
            player.sendPluginMessage(
                RecipeMessage.newWorkstation(station.template, station.id)
            )
        }
        player.sendPluginMessage(
            RecipeMessage.allDone()
        )
