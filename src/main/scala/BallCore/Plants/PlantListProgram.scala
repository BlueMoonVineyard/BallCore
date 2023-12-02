// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Plants

import BallCore.Datekeeping.Season
import BallCore.UI.Elements.*
import BallCore.UI.{UIProgram, UIServices}
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.{NamedTextColor, TextColor}

import scala.concurrent.Future

class PlantListProgram extends UIProgram:
    case class Flags(climate: Climate)

    case class Model(climate: Climate)

    case class Message()

    private val springColor: TextColor = TextColor.fromHexString("#1ff1a0")
    val spring: Component = txt"Spring".color(springColor)
    private val summerColor: TextColor = TextColor.fromHexString("#ff6c7f")
    val summer: Component = txt"Summer".color(summerColor)
    private val autumnColor: TextColor = TextColor.fromHexString("#ffb256")
    val autumn: Component = txt"Autumn".color(autumnColor)
    private val winterColor: TextColor = TextColor.fromHexString("#ffffff")
    val winter: Component = txt"Winter".color(winterColor)

    private val allColor: TextColor = TextColor.fromHexString("#926ee4")
    val allYear: Component = txt"all seasons".color(allColor)
    val allClimates: Component = txt"all".color(allColor)

    private val coldColor: TextColor = TextColor.fromHexString("#8adcff")
    private val cold: Component = txt("cold").color(coldColor)
    private val warmColor: TextColor = TextColor.fromHexString("#ff8999")
    private val warm: Component = txt("warm").color(warmColor)
    private val aridColor: TextColor = TextColor.fromHexString("#ffb090")
    private val arid: Component = txt("arid").color(aridColor)
    private val humidColor: TextColor = TextColor.fromHexString("#4cf4b3")
    private val humid: Component = txt("humid").color(humidColor)

    val warmArid = txt"$warm and $arid"
    val warmHumid = txt"$warm and $humid"
    val coldArid = txt"$cold and $arid"
    val coldHumid = txt"$cold and $humid"

    override def init(flags: Flags): Model =
        Model(flags.climate)

    override def view(model: Model): Callback ?=> Gui =
        Root(txt"Plants", 6) {
            OutlinePane(0, 0, 9, 6) {
                Plant.values.foreach { plant =>
                    Item(
                        plant.plant.representativeItem(),
                        Some(plant.name.color(plant.growingSeason match
                            case GrowingSeason.specific(Season.spring) =>
                                springColor
                            case GrowingSeason.specific(Season.summer) =>
                                summerColor
                            case GrowingSeason.specific(Season.autumn) =>
                                autumnColor
                            case GrowingSeason.specific(Season.winter) =>
                                winterColor
                            case GrowingSeason.allYear => allColor
                        )),
                    ) {
                        Lore(txt"Grows during ${plant.growingSeason match
                                case GrowingSeason.specific(Season.spring) =>
                                    spring
                                case GrowingSeason.specific(Season.summer) =>
                                    summer
                                case GrowingSeason.specific(Season.autumn) =>
                                    autumn
                                case GrowingSeason.specific(Season.winter) =>
                                    winter
                                case GrowingSeason.allYear => allYear
                            }".color(NamedTextColor.GRAY))
                        Lore(txt"Grows in ${plant.growingClimate match
                                case GrowingClimate.specific(Climate.warmArid) =>
                                    warmArid
                                case GrowingClimate.specific(Climate.warmHumid) =>
                                    warmHumid
                                case GrowingClimate.specific(Climate.coldArid) =>
                                    coldArid
                                case GrowingClimate.specific(Climate.coldHumid) =>
                                    coldHumid
                                case GrowingClimate.allClimates => allClimates
                            } climates".color(NamedTextColor.GRAY))
                        if plant.growingClimate.growsWithin(model.climate) then
                            Lore(txt"Grows in the current climate".color(NamedTextColor.GREEN))
                        plant.plant match
                            case PlantType.ageable(_, hoursBetweenStages) =>
                                Lore(
                                    txt"Takes ${txt(hoursBetweenStages.toString).color(NamedTextColor.WHITE)} ingame hours to grow one stage"
                                        .color(NamedTextColor.GRAY)
                                )
                            case PlantType.generateTree(
                                    sapling,
                                    kind,
                                    growthTime,
                                ) =>
                                Lore(
                                    txt"Takes ${txt(growthTime.toString).color(NamedTextColor.WHITE)} ingame hours to fully grow"
                                        .color(NamedTextColor.GRAY)
                                )
                            case PlantType.stemmedAgeable(
                                    stem,
                                    fruit,
                                    hoursBetweenStages,
                                ) =>
                                Lore(
                                    txt"Takes ${txt(hoursBetweenStages.toString).color(NamedTextColor.WHITE)} ingame hours to grow one stage"
                                        .color(NamedTextColor.GRAY)
                                )
                            case PlantType.verticalPlant(
                                    what,
                                    hoursBetweenStages,
                                ) =>
                                Lore(
                                    txt"Takes ${txt(hoursBetweenStages.toString).color(NamedTextColor.WHITE)} ingame hours to grow one stage"
                                        .color(NamedTextColor.GRAY)
                                )
                            case PlantType.bamboo(hoursBetweenStages) =>
                                Lore(
                                    txt"Takes ${txt(hoursBetweenStages.toString).color(NamedTextColor.WHITE)} ingame hours to grow one stage"
                                        .color(NamedTextColor.GRAY)
                                )
                            case PlantType.fruitTree(
                                    looksLike,
                                    fruit,
                                    growthTime,
                                ) =>
                                Lore(
                                    txt"Takes ${txt(growthTime.toString).color(NamedTextColor.WHITE)} ingame hours to fully grow"
                                        .color(NamedTextColor.GRAY)
                                )
                    }
                }
            }
        }

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        model
