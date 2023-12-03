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
import org.bukkit.inventory.ItemStack
import scala.util.chaining._

import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

class StationListProgram(stations: List[CraftingStation]) extends UIProgram:
    private val paginated: List[List[CraftingStation]] =
        stations.grouped(5 * 9).toList
    private val numPages: Int = paginated.size

    case class Flags(page: Int)
    case class Model(page: Int)

    enum Message:
        case clickStation(station: CraftingStation)
        case nextPage
        case prevPage

    override def init(flags: Flags): Model =
        Model(flags.page)

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.clickStation(station) =>
                val p = RecipeViewerProgram(stations, station.recipes)
                services.transferTo(p, p.Flags(model.page))
                model
            case Message.nextPage =>
                model.copy(page = (model.page + 1).min(numPages - 1))
            case Message.prevPage =>
                model.copy(page = (model.page - 1).max(0))

    override def view(model: Model): Callback ?=> Gui =
        Root(txt"Workstations (Page ${model.page + 1} of $numPages)", 6) {
            OutlinePane(0, 0, 9, 5) {
                paginated(model.page).foreach { (station) =>
                    Button(
                        station.template,
                        station.template.getItemMeta().displayName().color(NamedTextColor.GREEN),
                        Message.clickStation(station),
                    ) {
                        station.template.lore().forEach { component =>
                            Lore(component)
                        }
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

class RecipeViewerProgram(stations: List[CraftingStation], recipes: List[Recipe]) extends UIProgram:
    private val paginated: List[List[(Recipe, Int)]] =
        recipes.zipWithIndex.grouped(5 * 9).toList
    private val numPages: Int = paginated.size

    case class Flags(returnToPage: Int)
    case class Model(returnToPage: Int, page: Int)

    enum Message:
        case backToList
        case nextPage
        case prevPage

    override def init(flags: Flags): Model =
        Model(flags.returnToPage, 0)

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.backToList =>
                val p = StationListProgram(stations)
                services.transferTo(p, p.Flags(model.returnToPage))
                model
            case Message.nextPage =>
                model.copy(page = (model.page + 1).min(numPages - 1))
            case Message.prevPage =>
                model.copy(page = (model.page - 1).max(0))

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
                    .mkComponent(txt" or ")
            case Custom(choices: _*) =>
                choices
                    .map(mat =>
                        nameOf(mat)
                            .style(NamedTextColor.GRAY, TextDecoration.BOLD)
                    )
                    .toList
                    .mkComponent(txt" or ")
            case TagList(tag) =>
                tag.getValues.asScala
                    .map(mat =>
                        nameOf(ItemStack(mat))
                            .style(NamedTextColor.GRAY, TextDecoration.BOLD)
                    )
                    .toList
                    .mkComponent(txt" or ")

    private def nameOf(s: ItemStack): Component =
        if s.getItemMeta.hasDisplayName then s.getItemMeta.displayName()
        else Component.translatable(s)

    override def view(model: Model): Callback ?=> Gui =
        Root(txt"Recipes (Page ${model.page + 1} of $numPages)", 6) {
            OutlinePane(0, 0, 9, 5) {
                paginated(model.page).foreach { (recipe, _) =>
                    Item(
                        recipe.outputs.head._1.clone().tap(_.setAmount(1)),
                        Some(txt"${recipe.name}".color(NamedTextColor.GREEN)),
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
                    Material.OAK_DOOR,
                    txt"Back to List Of Stations".color(NamedTextColor.GREEN),
                    Message.backToList,
                )()
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
