package BallCore.Ferrobyte

import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Material
import BallCore.TextComponents._
import BallCore.CustomItems.Listeners
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBreakEvent
import BallCore.CustomItems.BlockManager
import BallCore.Storage.SQLManager
import net.kyori.adventure.text.Component
import io.circe.Encoder
import io.circe.Json
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import io.circe.Decoder
import io.circe.HCursor
import io.circe.Decoder.Result
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.joml.Vector3f
import org.joml.AxisAngle4f
import io.circe.generic.semiauto._
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Entity
import org.bukkit.Location
import cats.data.OptionT
import java.util.UUID
import BallCore.UI.UIProgram
import scala.concurrent.Future
import BallCore.UI.UIServices
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import cats.effect.IO
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import BallCore.Folia.EntityExecutionContext
import org.bukkit.plugin.Plugin
import net.kyori.adventure.text.serializer.ComponentSerializer
import net.kyori.adventure.text.format.NamedTextColor
import BallCore.UI.UIProgramRunner
import org.bukkit.Bukkit
import BallCore.UI.Prompts
import org.bukkit.block.Block

enum TextDirection:
    case billboard
    case north
    case south
    case west
    case east

    def move(e: Entity, it: Location => Unit): Unit =
        val loc = e.getLocation()
        it(loc)
        val _ = e.teleportAsync(loc)
    def applyTo(text: TextDisplay): Unit =
        this match
            case TextDirection.billboard =>
                text.setBillboard(Billboard.VERTICAL)
            case TextDirection.north =>
                text.setBillboard(Billboard.FIXED)
                move(text, _.setYaw(180f))
            case TextDirection.south =>
                text.setBillboard(Billboard.FIXED)
                move(text, _.setYaw(0f))
            case TextDirection.east =>
                text.setBillboard(Billboard.FIXED)
                move(text, _.setYaw(-90f))
            case TextDirection.west =>
                text.setBillboard(Billboard.FIXED)
                move(text, _.setYaw(90f))

object TextDirection:
    given Encoder[TextDirection] = deriveEncoder[TextDirection]
    given Decoder[TextDirection] = deriveDecoder[TextDirection]

object TextProjector:
    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "text_projector"),
        Material.REDSTONE_LAMP,
        txt"Text Projector",
        txt"Projects text into the world for all to see",
    )

given Encoder[Component] =
    new Encoder[Component]:
        override def apply(a: Component): Json =
            Json.fromString(JSONComponentSerializer.json().serialize(a))

given Decoder[Component] =
    new Decoder[Component]:
        override def apply(c: HCursor): Result[Component] =
            c.as[String].map(JSONComponentSerializer.json().deserialize)

val mm: ComponentSerializer[Component, Component, String] =
    MiniMessage
        .builder()
        .tags(
            TagResolver
                .builder()
                .resolver(StandardTags.color())
                .resolver(StandardTags.decorations())
                .resolver(StandardTags.rainbow())
                .resolver(StandardTags.gradient())
                .build()
        )
        .build()

class TextEditor()(using sql: SQLManager, p: Plugin, bm: BlockManager)
    extends UIProgram:
    import BallCore.UI.Elements._

    case class Model(
        text: Component,
        offset: Float,
        direction: TextDirection,
        entity: TextDisplay,
        block: Block,
    )
    case class Flags(
        text: Component,
        offset: Float,
        direction: TextDirection,
        entity: TextDisplay,
        block: Block,
    )
    enum Message:
        case editText
        case goUp
        case goDown
        case toggleDirection

    override def init(flags: Flags): Model =
        Model(
            flags.text,
            flags.offset,
            flags.direction,
            flags.entity,
            flags.block,
        )
    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.editText =>
                sql.useFuture(for
                    result <- IO.fromFuture {
                        IO {
                            services.prompt(
                                "Enter the new text in MiniMessage format."
                            )
                        }
                    }
                    parsed = mm.deserialize(result)
                    _ <- IO { model.entity.text(parsed) }
                        .evalOn(EntityExecutionContext(model.entity))
                    _ <- sql.withS(
                        bm.store(
                            model.block,
                            "projected",
                            (parsed, model.offset, model.direction),
                        )
                    )
                yield model.copy(text = parsed))
            case Message.goUp =>
                sql.useFuture(
                    for
                        _ <- IO {
                            model.entity.setTransformation(
                                Transformation(
                                    Vector3f(0, model.offset + 0.1f, 0),
                                    AxisAngle4f(),
                                    Vector3f(1f),
                                    AxisAngle4f(),
                                )
                            )
                        }.evalOn(EntityExecutionContext(model.entity))
                        _ <- sql.withS(
                            bm.store(
                                model.block,
                                "projected",
                                (
                                    model.text,
                                    model.offset + 0.1f,
                                    model.direction,
                                ),
                            )
                        )
                    yield model.copy(offset = model.offset + 0.1f)
                )
            case Message.goDown =>
                sql.useFuture(
                    for
                        _ <- IO {
                            model.entity.setTransformation(
                                Transformation(
                                    Vector3f(0, model.offset - 0.1f, 0),
                                    AxisAngle4f(),
                                    Vector3f(1f),
                                    AxisAngle4f(),
                                )
                            )
                        }.evalOn(EntityExecutionContext(model.entity))
                        _ <- sql.withS(
                            bm.store(
                                model.block,
                                "projected",
                                (
                                    model.text,
                                    model.offset - 0.1f,
                                    model.direction,
                                ),
                            )
                        )
                    yield model.copy(offset = model.offset - 0.1f)
                )
            case Message.toggleDirection =>
                val newDirection =
                    model.direction match
                        case TextDirection.billboard =>
                            TextDirection.north
                        case TextDirection.north =>
                            TextDirection.south
                        case TextDirection.south =>
                            TextDirection.west
                        case TextDirection.west =>
                            TextDirection.east
                        case TextDirection.east =>
                            TextDirection.billboard
                sql.useFuture(
                    for
                        _ <- sql.withS(
                            bm.store(
                                model.block,
                                "projected",
                                (model.text, model.offset, newDirection),
                            )
                        )
                        _ <- IO { newDirection.applyTo(model.entity) }
                            .evalOn(EntityExecutionContext(model.entity))
                    yield model.copy(direction = newDirection)
                )
    override def view(model: Model): Callback ?=> Gui =
        Root(txt"Editing Text", 1) {
            OutlinePane(0, 0, 1, 1) {
                Button(
                    Material.OAK_SIGN,
                    txt"Edit Text",
                    Message.editText,
                ) {
                    Lore(txt"Current text:".color(NamedTextColor.GRAY))
                    Lore(model.text.colorIfAbsent(NamedTextColor.WHITE))
                }
            }
            OutlinePane(1, 0, 1, 1) {
                Button(
                    Material.COMPASS,
                    txt"Change Direction",
                    Message.toggleDirection,
                ) {
                    def line(of: Component, self: TextDirection): Unit =
                        if model.direction == self then
                            Lore(txt"● $of".color(NamedTextColor.GREEN))
                        else Lore(txt"○ $of".color(NamedTextColor.GRAY))

                    line(txt"Billboard", TextDirection.billboard)
                    line(txt"Facing North", TextDirection.north)
                    line(txt"Facing South", TextDirection.south)
                    line(txt"Facing West", TextDirection.west)
                    line(txt"Facing East", TextDirection.east)
                }
            }
            OutlinePane(7, 0, 1, 1) {
                Button(
                    Material.LIME_DYE,
                    txt"Shift Up",
                    Message.goUp,
                )()
            }
            OutlinePane(8, 0, 1, 1) {
                Button(
                    Material.RED_DYE,
                    txt"Shift Down",
                    Message.goDown,
                )()
            }
        }

class TextProjector(using
    bm: BlockManager,
    sql: SQLManager,
    p: Plugin,
    prompts: Prompts,
) extends CustomItem,
      Listeners.BlockPlaced,
      Listeners.BlockRemoved,
      Listeners.BlockClicked:
    override def group: ItemGroup = Ferrobyte.group
    override def template: CustomItemStack = TextProjector.template

    override def onBlockPlace(event: BlockPlaceEvent): Unit =
        val text = txt"Click the projector base to edit me!"
        val offset = 1f
        val direction = TextDirection.billboard

        val entity =
            event.getBlock.getWorld.spawn(
                event.getBlock.getLocation.add(0.5d, 1d, 0.5d),
                classOf[TextDisplay],
            )
        entity.text(text)
        entity.setTransformation(
            Transformation(
                Vector3f(0f, offset, 0f),
                AxisAngle4f(),
                Vector3f(1f),
                AxisAngle4f(),
            )
        )
        direction.applyTo(entity)

        sql.useBlocking(
            sql.withS(
                sql.withTX(
                    for
                        _ <- bm
                            .store(
                                event.getBlock,
                                "projected",
                                (text, offset, direction),
                            )
                        _ <- bm
                            .store(event.getBlock, "entity", entity.getUniqueId)
                    yield ()
                )
            )
        )
    override def onBlockClicked(event: PlayerInteractEvent): Unit =
        sql.useBlocking(sql.withS(sql.withTX((for
            projected <- OptionT(
                bm.retrieve[(Component, Float, TextDirection)](
                    event.getClickedBlock,
                    "projected",
                )
            )
            entity <- OptionT(
                bm.retrieve[UUID](event.getClickedBlock, "entity")
            )
            (text, offset, direction) = projected
        yield (text, offset, direction, entity)).value)))
            .foreach { (text, offset, direction, entity) =>
                val p = TextEditor()
                val runner = UIProgramRunner(
                    p,
                    p.Flags(
                        text,
                        offset,
                        direction,
                        Bukkit.getEntity(entity).asInstanceOf[TextDisplay],
                        event.getClickedBlock,
                    ),
                    event.getPlayer,
                )
                runner.render()
            }
    override def onBlockRemoved(event: BlockBreakEvent): Unit =
        sql.useBlocking(sql.withS(bm.retrieve[UUID](event.getBlock, "entity")))
            .foreach { (entity) =>
                Bukkit.getEntity(entity).remove()
            }
        sql.useFireAndForget(sql.withS(for
            _ <- bm.remove(event.getBlock, "projected")
            _ <- bm.remove(event.getBlock, "entity")
        yield ()))
