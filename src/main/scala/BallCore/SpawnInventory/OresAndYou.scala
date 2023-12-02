package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player
import cats.effect.IO
import net.kyori.adventure.inventory.Book
import BallCore.Folia.EntityExecutionContext
import org.bukkit.plugin.Plugin
import BallCore.Acclimation.Information
import skunk.Session

object OresAndYou:
    val builder = MiniMessage.builder().strict(true).build()
    def percent(d: Double): String =
        f"${d*100}%.1f%%"
    def viewForPlayer(plr: Player)(using p: Plugin, s: Session[IO], as: BallCore.Acclimation.Storage): IO[Book] =
        for {
            pair <- IO { Information.latLong(plr.getX, plr.getZ) }.evalOn(EntityExecutionContext(plr))
            plat <- as.getLatitude(plr.getUniqueId)
            plong <- as.getLongitude(plr.getUniqueId)
            dlat = Information.similarityNeg(pair._1, plat)
            dlong = Information.similarityNeg(pair._2, plong)
            bonusRateMultiplier = ((dlat + dlong) / 2.0).abs
        } yield
            val pages =
                List(
                    s"""Ores And You
                       |
                       |<b>Table of Contents</b>
                       |- <link:2>Adaptation</link>
                       |- <link:4>Universal Ores</link>
                       |- <link:5>Southeast Ores</link>
                       |- <link:6>Southwest Ores</link>
                       |- <link:7>Northwest Ores</link>
                       |- <link:8>Northeast Ores</link>
                       |- <link:9>North Ores</link>
                       |- <link:10>South Ores</link>
                       |- <link:11>West Ores</link>
                       |- <link:12>East Ores</link>""".stripMargin,
                    s"""<b>Adaptation</b>
                       |Your adaptation to your current location is: ${percent(bonusRateMultiplier)}
                       |(ore probabilities are multipled by your adaptation)""".stripMargin,
                    s"""Adaptation Speeds:
                       |- 7 days within your heart's claim
                       |- 14 days if you have no heart
                       |- 30 days if you're outside of your heart's claim""".stripMargin,
                    s"""<b>Univeral Ores</b>
                       |Coal spawns from y=0 to 320.
                       |Redstone spawns from y=-64 to 16.""".stripMargin,
                    s"""<b>Southeast Ores</b>
                       |Iron spawns from y=-64 to 320.
                       |Gold spawns from y=-64 to 32.
                       |Orichalcum spawns from y=-16 to 112.
                       |
                       |Iron and Gold make Gilded Iron.""".stripMargin,
                    s"""<b>Southwest Ores</b>
                       |Tin spawns from y=-64 to 320.
                       |Silver spawns from y=-64 to 32.
                       |Copper spawns from y=-16 to 112.
                       |
                       |Tin and Copper make Bronze.""".stripMargin,
                    s"""<b>Northwest Ores</b>
                       |Aluminum spawns from y=-64 to 320.
                       |Palladium spawns from y=-64 to 32.
                       |Hihi'irogane spawns from y=-16 to 112.
                       |
                       |Aluminum and Palladiumm make Pallalumin.""".stripMargin,
                    s"""<b>Northwest Ores</b>
                       |Zinc spawns from y=-64 to 320.
                       |Magnesium spawns from y=-64 to 32.
                       |Meteorite spawns from y=-16 to 112.
                       |
                       |Magnesium and Meteorite make Magnox.""".stripMargin,
                    s"""<b>North Ores</b>
                       |Sulfur spawns from y=0 to 320.
                       |Sapphire spawns from y=-64 to 16.""".stripMargin,
                    s"""<b>South Ores</b>
                       |Silicon spawns from y=0 to 320.
                       |Diamond spawns from y=-64 to 16.""".stripMargin,
                    s"""<b>West Ores</b>
                       |Lead spawns from y=0 to 320.
                       |Emerald spawns from y=-64 to 16.""".stripMargin,
                    s"""<b>East Ores</b>
                       |Cobalt spawns from y=0 to 320.
                       |Plutonium spawns from y=-64 to 16.""".stripMargin,
                ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

            val book = Book
                .book(txt"Ores And You", txt"Janet", pages.asJava)

            book
