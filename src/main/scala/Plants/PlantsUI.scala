// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Plants

import BallCore.UI.UIProgram
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import BallCore.UI.UIServices
import scala.concurrent.Future
import BallCore.UI.Elements._
import BallCore.Datekeeping.Season
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.NamedTextColor

class PlantListProgram extends UIProgram:
    case class Flags()
    case class Model()
    case class Message()

    val springColor = TextColor.fromHexString("#1ff1a0")
    val spring = txt"Spring".color(springColor)
    val summerColor = TextColor.fromHexString("#ff6c7f")
    val summer = txt"Summer".color(summerColor)
    val autumnColor = TextColor.fromHexString("#ffb256")
    val autumn = txt"Autumn".color(autumnColor)
    val winterColor = TextColor.fromHexString("#ffffff")
    val winter = txt"Winter".color(winterColor)

    val allColor = TextColor.fromHexString("#926ee4")
    val allYear = txt"all seasons".color(allColor)
    val allClimates = txt"all".color(allColor)

    val coldColor = TextColor.fromHexString("#8adcff")
    val cold = txt("cold").color(coldColor)
    val warmColor = TextColor.fromHexString("#ff8999")
    val warm = txt("warm").color(warmColor)
    val aridColor = TextColor.fromHexString("#ffb090")
    val arid = txt("arid").color(aridColor)
    val humidColor = TextColor.fromHexString("#4cf4b3")
    val humid = txt("humid").color(humidColor)

    val warmArid = txt"${warm} and ${arid}"
    val warmHumid = txt"${warm} and ${humid}"
    val coldArid = txt"${cold} and ${arid}"
    val coldHumid = txt"${cold} and ${humid}"

    override def init(flags: Flags): Model =
        Model()
    override def view(model: Model): Callback ?=> Gui =
        Root(txt"Plants", 6) {
            OutlinePane(0, 0, 9, 6) {
                Plant.values.foreach { plant =>
                    
                    Item(plant.plant.representativeItem(), Some(plant.name.color(plant.growingSeason match
                        case GrowingSeason.specific(Season.spring) => springColor 
                        case GrowingSeason.specific(Season.summer) => summerColor 
                        case GrowingSeason.specific(Season.autumn) => autumnColor
                        case GrowingSeason.specific(Season.winter) => winterColor
                        case GrowingSeason.allYear => allColor
                    ))) {
                        Lore(txt"Grows during ${
                            plant.growingSeason match
                                case GrowingSeason.specific(Season.spring) => spring
                                case GrowingSeason.specific(Season.summer) => summer
                                case GrowingSeason.specific(Season.autumn) => autumn
                                case GrowingSeason.specific(Season.winter) => winter
                                case GrowingSeason.allYear => allYear
                        }".color(NamedTextColor.GRAY))
                        Lore(txt"Grows in ${
                            plant.growingClimate match
                                case GrowingClimate.specific(Climate.warmArid) => warmArid
                                case GrowingClimate.specific(Climate.warmHumid) => warmHumid
                                case GrowingClimate.specific(Climate.coldArid) => coldArid
                                case GrowingClimate.specific(Climate.coldHumid) => coldHumid
                                case GrowingClimate.allClimates => allClimates
                        } climates".color(NamedTextColor.GRAY))
                        plant.plant match
                            case PlantType.ageable(_, hoursBetweenStages) =>
                                Lore(txt"Takes ${txt(hoursBetweenStages.toString()).color(NamedTextColor.WHITE)} ingame hours to grow one stage".color(NamedTextColor.GRAY))
                            case PlantType.generateTree(sapling, kind, growthTime) =>
                                Lore(txt"Takes ${txt(growthTime.toString()).color(NamedTextColor.WHITE)} ingame hours to fully grow".color(NamedTextColor.GRAY))
                            case PlantType.stemmedAgeable(stem, fruit, hoursBetweenStages) =>
                                Lore(txt"Takes ${txt(hoursBetweenStages.toString()).color(NamedTextColor.WHITE)} ingame hours to grow one stage".color(NamedTextColor.GRAY))
                            case PlantType.verticalPlant(what, hoursBetweenStages) =>
                                Lore(txt"Takes ${txt(hoursBetweenStages.toString()).color(NamedTextColor.WHITE)} ingame hours to grow one stage".color(NamedTextColor.GRAY))
                            case PlantType.bamboo(hoursBetweenStages) =>
                                Lore(txt"Takes ${txt(hoursBetweenStages.toString()).color(NamedTextColor.WHITE)} ingame hours to grow one stage".color(NamedTextColor.GRAY))
                            case PlantType.fruitTree(looksLike, fruit, growthTime) =>
                                Lore(txt"Takes ${txt(growthTime.toString()).color(NamedTextColor.WHITE)} ingame hours to fully grow".color(NamedTextColor.GRAY))
                    }
                }
            }
        }
    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        model
