// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.UI.Elements.*
import BallCore.UI.{UIProgram, UIServices}
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import scala.util.chaining._

import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import BallCore.Storage.SQLManager
import cats.effect.IO
import BallCore.CraftingStations.CraftingActor.validateJob
import BallCore.CustomItems.ItemRegistry
import BallCore.Folia.LocationExecutionContext
import org.bukkit.plugin.Plugin

class RecipeSelectorProgram(recipes: List[Recipe])(using
    actor: CraftingActor,
    sql: SQLManager,
    ir: ItemRegistry,
    p: Plugin,
) extends UIProgram:

    import io.circe.generic.auto.*

    private val paginated: List[List[(Recipe, Int)]] =
        recipes.zipWithIndex.grouped(5 * 9).toList
    private val numPages: Int = paginated.size

    case class Flags(player: Player, factory: Block)

    case class Model(player: Player, factory: Block, page: Int, repeat: Boolean)

    enum Message:
        case selectRecipe(index: Int)
        case nextPage
        case prevPage
        case toggleRepeat

    override def init(flags: Flags): Model =
        Model(flags.player, flags.factory, 0, false)

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.selectRecipe(index) =>
                sql.useFuture(for {
                    ok <- IO { validateJob(recipes(index), model.factory) }
                        .evalOn(
                            LocationExecutionContext(
                                model.factory.getLocation()
                            )
                        )
                    _ <-
                        if ok then
                            IO {
                                actor.send(
                                    CraftingMessage.startWorking(
                                        model.player,
                                        model.factory,
                                        recipes(index),
                                        model.repeat,
                                    )
                                )
                                services.notify(
                                    "You've started working on that recipe!"
                                )
                            }
                        else
                            IO {
                                services.notify(
                                    "You don't have the ingredients for that recipe! Make sure they're in a chest adjacent to the workstation."
                                )
                            }
                } yield model)
            case Message.nextPage =>
                model.copy(page = (model.page + 1).min(numPages - 1))
            case Message.prevPage =>
                model.copy(page = (model.page - 1).max(0))
            case Message.toggleRepeat =>
                model.copy(repeat = !model.repeat)

    private def choiceToString(input: RecipeIngredient): Component =
        import RecipeIngredient._

        input match
            case Vanilla(choices: _*) =>
                choices
                    .map(mat =>
                        nameOf(ItemStack(mat))
                            .style(NamedTextColor.GRAY, TextDecoration.BOLD)
                    )
                    .toList
                    .mkComponent(trans"ui.choice-separator")
            case Custom(choices: _*) =>
                choices
                    .map(mat =>
                        nameOf(mat)
                            .style(NamedTextColor.GRAY, TextDecoration.BOLD)
                    )
                    .toList
                    .mkComponent(trans"ui.choice-separator")
            case TagList(tag) =>
                tag.getValues.asScala
                    .map(mat =>
                        nameOf(ItemStack(mat))
                            .style(NamedTextColor.GRAY, TextDecoration.BOLD)
                    )
                    .toList
                    .mkComponent(trans"ui.choice-separator")

    private def nameOf(s: ItemStack): Component =
        if s.getItemMeta.hasDisplayName then s.getItemMeta.displayName()
        else Component.translatable(s)

    override def view(model: Model): Callback ?=> Gui =
        Root(trans"ui.recipe-selector.title".args((model.page + 1).toComponent, numPages.toComponent), 6) {
            OutlinePane(0, 0, 9, 5) {
                paginated(model.page).foreach { (recipe, idx) =>
                    Button(
                        recipe.outputs.head._1.clone().tap(_.setAmount(1)),
                        recipe.name.toComponent.color(NamedTextColor.GREEN),
                        Message.selectRecipe(idx),
                    ) {
                        Lore(
                            trans"ui.recipe-selector.ingredients"
                                .style(
                                    NamedTextColor.WHITE,
                                    TextDecoration.UNDERLINED,
                                )
                        )
                        recipe.inputs.foreach { (input, amount) =>
                            Lore(
                                txt" - ${choiceToString(input)} × $amount"
                                    .color(
                                        NamedTextColor.WHITE
                                    )
                            )
                        }
                        Lore(txt"")
                        Lore(
                            trans"ui.recipe-selector.results"
                                .style(
                                    NamedTextColor.WHITE,
                                    TextDecoration.UNDERLINED,
                                )
                        )
                        recipe.outputs.foreach { output =>
                            Lore(
                                txt" - ${nameOf(output._1)
                                        .style(NamedTextColor.GRAY, TextDecoration.BOLD)} × ${output._2}"
                                    .color(NamedTextColor.WHITE)
                            )
                            if output._1.getItemMeta.hasLore then
                                Lore(
                                    txt"   (${output._1.getItemMeta.lore().get(0)})"
                                        .color(NamedTextColor.DARK_PURPLE)
                                )
                        }
                        Lore(txt"")
                        val time = trans"ui.recipe-selector.seconds".args(recipe.work.toComponent).color(
                            NamedTextColor.GREEN
                        )
                        Lore(
                            trans"ui.recipe-selector.work-time".args(time).color(NamedTextColor.WHITE)
                        )
                        val players =
                            trans"ui.recipe-selector.n-players".args(recipe.minimumPlayersRequiredToWork.toComponent)
                                .color(
                                    NamedTextColor.GREEN
                                )
                        Lore(
                            trans"ui.recipe-selector.requires-players-working".args(players).color(
                                NamedTextColor.WHITE
                            )
                        )
                    }
                }
            }
            OutlinePane(0, 5, 9, 1) {
                Button(
                    Material.RED_DYE,
                    trans"ui.previous-page".color(NamedTextColor.GREEN),
                    Message.prevPage,
                )()
                Button(
                    Material.LIME_DYE,
                    trans"ui.next-page".color(NamedTextColor.GREEN),
                    Message.nextPage,
                )()
                Button(
                    Material.REDSTONE_TORCH,
                    if model.repeat then
                        trans"ui.recipe-selector.repeat.on".color(NamedTextColor.GREEN)
                    else trans"ui.recipe-selector.repeat.off".color(NamedTextColor.RED),
                    Message.toggleRepeat,
                ) {
                    if model.repeat then
                        Lore(
                            trans"ui.recipe-selector.repeat.turn-off"
                                .color(NamedTextColor.GRAY)
                        )
                    else
                        Lore(
                            trans"ui.recipe-selector.repeat.turn-on"
                                .color(NamedTextColor.GRAY)
                        )
                }
            }
        }
