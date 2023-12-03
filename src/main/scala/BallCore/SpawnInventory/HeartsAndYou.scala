package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player
import cats.effect.IO
import net.kyori.adventure.inventory.Book
import BallCore.Beacons.CivBeaconManager

object HeartsAndYou:
    val builder = MiniMessage.builder().strict(true).build()
    def percent(d: Double): String =
        f"${d*100}%.1f%%"
    def viewForPlayer(plr: Player): IO[Book] =
      val pages =
          List(
              s"""Hearts And You
                 |
                 |<b>Table of Contents</b>
                 |- <link:2>Hearts vs. Beacons</link>
                 |- <link:3>Binding Hearts</link>
                 |- <link:4>Protection Areas</link>
                 |- <link:5>Expanding Beacons</link>
                 |- <link:6>Subgroup Claims</link>
                 |- <link:8>Table Of Beacon Sizes</link>""".stripMargin,
              s"""<b>Hearts vs. Beacon</b>
                 |A heart is a standalone block, whilst a beacon is the structure of one or more hearts that can protect land.""".stripMargin,
              s"""<b>Binding Hearts</b>
                 |A heart can be bound to a group, using either the Groups UI or the /bind-heart command.
                 |This will turn it into a beacon, which can protect land.""".stripMargin,
              s"""<b>Protection Areas</b>
                 |Once bound to a group, beacons can be right-clicked to create or edit their coverage area.
                 |Group permissions are applied in the coverage area.
                 |By default, this prevents outsiders from building and using chests, among other things.""".stripMargin,
              s"""<b>Expanding Beacons</b>
                 |After one player binds a heart, other player can place their hearts on the faces of the existing beacon to expand it.
                 |This increases the area it can cover.""".stripMargin,
              s"""<b>Subgroup Claims</b>
                 |Subgroup claims allow groups to divide up different areas of the world and manage their permissions individually.
                 |They're only enforced within beacons bound to that group, though.""".stripMargin,
              s"""<b>Table of Beacon Sizes</b>
                 |Hearts - Max Chunks
                 |1 - ${CivBeaconManager.populationToArea(1) / 256}
                 |2 - ${CivBeaconManager.populationToArea(2) / 256}
                 |3 - ${CivBeaconManager.populationToArea(3) / 256}
                 |4 - ${CivBeaconManager.populationToArea(4) / 256}
                 |5 - ${CivBeaconManager.populationToArea(5) / 256}
                 |6 - ${CivBeaconManager.populationToArea(6) / 256}
                 |7 - ${CivBeaconManager.populationToArea(7) / 256}
                 |8 - ${CivBeaconManager.populationToArea(8) / 256}
                 |9 - ${CivBeaconManager.populationToArea(9) / 256}
                 |10 - ${CivBeaconManager.populationToArea(10) / 256}""".stripMargin,
          ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

      val book = Book
          .book(txt"Hearts And You", txt"Janet", pages.asJava)

      IO.pure(book)
