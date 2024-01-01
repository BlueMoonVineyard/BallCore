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
import org.bukkit.Material
import BallCore.CustomItems.CustomItemStack
import org.bukkit.Tag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import io.circe.jawn.decode
import BallCore.CraftingStations.RecipeIngredient

enum NetRecipeIngredient:
    case vanilla(oneOf: List[Material])
    case custom(oneOf: List[CustomItemStack])
    case tagList(tag: Tag[Material])

object NetRecipeIngredient:
    given Encoder[NamespacedKey] =
        new Encoder[NamespacedKey]:
            override def apply(a: NamespacedKey): Json =
                a.toString.asJson
    given Encoder[Material] =
        new Encoder[Material]:
            override def apply(a: Material): Json =
                Json.fromString(a.getKey().toString())
    given Encoder[Tag[Material]] =
        new Encoder[Tag[Material]]:
            override def apply(a: Tag[Material]): Json =
                Json.fromString(a.getKey().toString())
    given Encoder[CustomItemStack] =
        new Encoder[CustomItemStack]:
            override def apply(a: CustomItemStack): Json =
                Json.fromFields(
                    List(
                        ("id", a.id.asJson),
                        ("item", Option(a).map(_.serializeAsBytes()).asJson),
                    )
                )
    given Encoder[NetRecipeIngredient] = deriveEncoder[NetRecipeIngredient]

case class Recipe(
    name: Component,
    id: NamespacedKey,
    inputs: List[(NetRecipeIngredient, Int)],
    outputs: List[(ItemStack, Int)],
    work: Int,
    minimumPlayersRequiredToWork: Int,
)

object Recipe:
    def apply(from: BallCore.CraftingStations.Recipe): Recipe =
        Recipe(
            from.name,
            from.id,
            from.inputs.map { (ingredient, amount) =>
                val it = ingredient match
                    case RecipeIngredient.Vanilla(oneOf: _*) =>
                        NetRecipeIngredient.vanilla(oneOf.toList)
                    case RecipeIngredient.Custom(oneOf: _*) =>
                        NetRecipeIngredient.custom(oneOf.toList)
                    case RecipeIngredient.TagList(tag) =>
                        NetRecipeIngredient.tagList(tag)
                (it, amount)
            },
            from.outputs,
            from.work,
            from.minimumPlayersRequiredToWork,
        )
    given Encoder[ItemStack] =
        new Encoder[ItemStack]:
            override def apply(a: ItemStack): Json =
                Option(a).map(_.serializeAsBytes()).asJson
    given Encoder[NamespacedKey] =
        new Encoder[NamespacedKey]:
            override def apply(a: NamespacedKey): Json =
                a.toString.asJson
    given Encoder[Component] =
        new Encoder[Component]:
            val json = JSONComponentSerializer.json()
            override def apply(a: Component): Json =
                decode[Json](json.serialize(a)).toOption.get

    given Encoder[Recipe] = deriveEncoder[Recipe]

enum RecipeMessage:
    case nowSending()
    case customItem(item: ItemStack, id: NamespacedKey)
    case newWorkstation(workstation: ItemStack, id: NamespacedKey)
    case newRecipe(workstation: NamespacedKey, recipe: Recipe)
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

class JoinListener(using p: Plugin, s: List[CraftingStation], ir: ItemRegistry)
    extends Listener:
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
        player.sendPluginMessage(
            RecipeMessage.nowSending()
        )
        ir.items().foreach { item =>
            player.sendPluginMessage(
                RecipeMessage.customItem(item.template, item.id)
            )
        }
        s.foreach { station =>
            player.sendPluginMessage(
                RecipeMessage.newWorkstation(station.template, station.id)
            )
            station.recipes.foreach { recipe =>
                player.sendPluginMessage(
                    RecipeMessage.newRecipe(station.id, Recipe(recipe))
                )
            }
        }
        player.sendPluginMessage(
            RecipeMessage.allDone()
        )
