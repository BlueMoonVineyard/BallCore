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
import org.bukkit.inventory.RecipeChoice.{ExactChoice, MaterialChoice}
import org.bukkit.inventory.{ItemStack, RecipeChoice}

import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

class RecipeSelectorProgram(recipes: List[Recipe])(using actor: CraftingActor)
    extends UIProgram:

    import io.circe.generic.auto.*

    private val paginated: List[List[(Recipe, Int)]] =
        recipes.zipWithIndex.grouped(5 * 9).toList
    private val numPages: Int = paginated.size

    case class Flags(player: Player, factory: Block)

    case class Model(player: Player, factory: Block, page: Int)

    enum Message:
        case selectRecipe(index: Int)
        case nextPage
        case prevPage

    override def init(flags: Flags): Model =
        Model(flags.player, flags.factory, 0)

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.selectRecipe(index) =>
                actor.send(
                    CraftingMessage.startWorking(
                        model.player,
                        model.factory,
                        recipes(index),
                    )
                )
                services.notify("You've started working on that recipe!")
                model
            case Message.nextPage =>
                model.copy(page = (model.page + 1).min(numPages - 1))
            case Message.prevPage =>
                model.copy(page = (model.page - 1).max(0))

    private def choiceToString(input: RecipeChoice): Component =
        input match
            case m: MaterialChoice =>
                m.getChoices.asScala
                    .map(mat =>
                        nameOf(ItemStack(mat))
                            .style(NamedTextColor.GRAY, TextDecoration.BOLD)
                    )
                    .toList
                    .mkComponent(txt" or ")
            case e: ExactChoice =>
                e.getChoices.asScala
                    .map(it =>
                        nameOf(it)
                            .style(NamedTextColor.GRAY, TextDecoration.BOLD)
                    )
                    .toList
                    .mkComponent(txt" or ")
            case _ =>
                txt"TODO: $input"

    private def nameOf(s: ItemStack): Component =
        if s.getItemMeta.hasDisplayName then s.getItemMeta.displayName()
        else Component.translatable(s)

    override def view(model: Model): Callback ?=> Gui =
        Root(txt"Recipes (Page ${model.page + 1} of $numPages)", 6) {
            OutlinePane(0, 0, 9, 5) {
                paginated(model.page).foreach { (recipe, idx) =>
                    Button(
                        recipe.outputs.head.getType,
                        txt"${recipe.name}".color(NamedTextColor.GREEN),
                        Message.selectRecipe(idx),
                    ) {
                        Lore(
                            txt"Ingredients"
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
                            txt"Results"
                                .style(
                                    NamedTextColor.WHITE,
                                    TextDecoration.UNDERLINED,
                                )
                        )
                        recipe.outputs.foreach { output =>
                            Lore(
                                txt" - ${nameOf(output)
                                        .style(NamedTextColor.GRAY, TextDecoration.BOLD)} × ${output.getAmount}"
                                    .color(NamedTextColor.WHITE)
                            )
                            if output.getItemMeta.hasLore then
                                Lore(
                                    txt"   (${output.getItemMeta.lore().get(0)})"
                                        .color(NamedTextColor.DARK_PURPLE)
                                )
                        }
                        Lore(txt"")
                        val time = txt"${recipe.work} seconds".color(
                            NamedTextColor.GREEN
                        )
                        Lore(
                            txt"Takes $time of work".color(NamedTextColor.WHITE)
                        )
                        val players =
                            if recipe.minimumPlayersRequiredToWork > 1 then
                                txt"${recipe.minimumPlayersRequiredToWork} players"
                                    .color(
                                        NamedTextColor.GREEN
                                    )
                            else
                                txt"${recipe.minimumPlayersRequiredToWork} player"
                                    .color(
                                        NamedTextColor.GREEN
                                    )
                        Lore(
                            txt"Requires $players working".color(
                                NamedTextColor.WHITE
                            )
                        )
                    }
                }
            }
            OutlinePane(0, 5, 9, 1) {
                Button(
                    Material.RED_DYE,
                    txt"Previous Page".color(NamedTextColor.GREEN),
                    Message.prevPage,
                )()
                Button(
                    Material.LIME_DYE,
                    txt"Next Page".color(NamedTextColor.GREEN),
                    Message.nextPage,
                )()
            }
        }
