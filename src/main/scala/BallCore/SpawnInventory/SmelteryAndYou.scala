package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player
import cats.effect.IO
import net.kyori.adventure.inventory.Book

object SmelteryAndYou:
    val builder = MiniMessage.builder().strict(true).build()
    def viewForPlayer(plr: Player): IO[Book] =
        val pages =
            List(
                s"""Smeltery And You
                 |
                 |<b>Table of Contents</b>
                 |- <link:2>Ferrobyte</link>
                 |- <link:3>The Sky Bronzes</link>
                 |- <link:4>Suno</link>
                 |- <link:5>Adamantite</link>
                 |- <link:6>Hepatizon</link>
                 |- <link:7>Manyullyn</link>""".stripMargin,
                s"""<b>Ferrobyte</b>
                 |Ferrobyte is a technically-inclined alloy that can be used for creating gadgets such as the Backpack or the Text Projector.""".stripMargin,
                s"""<b>The Sky Bronzes</b>
                 |The various Sky Bronzes are used for crafting armor.
                 |Dawn Bronze protects you from a little bit of everything.
                 |Sky Bronze protects you from fire.
                 |Evening Bronze protects you from explosions.
                 |Star Bronze protects you from projectiles.""".stripMargin,
                s"""<b>Suno</b>
                 |Suno allows you to craft lucky tools.""".stripMargin,
                s"""<b>Adamantite</b>
                 |Adamantite allows you to craft wide tools that break 3×3 blocks.""".stripMargin,
                s"""<b>Hepatizon</b>
                 |Hepatizon allows you to craft fast tools.""".stripMargin,
                s"""<b>Manyullyn</b>
                 |Manyullyn allows you to craft sharp tools.""".stripMargin,
            ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

        val book = Book
            .book(txt"Smeltery And You", txt"Janet", pages.asJava)

        IO.pure(book)
