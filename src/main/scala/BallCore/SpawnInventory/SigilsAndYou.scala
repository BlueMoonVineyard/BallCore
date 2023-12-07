package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player
import cats.effect.IO
import net.kyori.adventure.inventory.Book

object SigilsAndYou:
    val builder = MiniMessage.builder().strict(true).build()
    def viewForPlayer(plr: Player): IO[Book] =
        val pages =
            List(
                s"""Sigils And You
                   |
                   |<b>Table of Contents</b>
                   |- <link:2>Sigils</link>
                   |- <link:3>Sigil Slimes</link>""".stripMargin,
                s"""<b>Sigils</b>
                   |When you kill someone with sigils in your inventory, one of them will become "bound" to the killed player.
                   |Infinitely many sigils can be bound to a player, provided you can keep killing them.""".stripMargin,
                s"""<b>Sigil Slimes</b>
                   |Sigil Slimes will hatch when you use a Sigil Slime egg on the ground.
                   |They will be bound to the same beacon as you are at the time of their birth.""".stripMargin,
                s"""If you wedge a bound sigil into the slime, the player it's bound to will be unable to enter the beacon area until the slime dies.
                   |They will know where the slime is if it blocks them.""".stripMargin,
            ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

        val book = Book
            .book(txt"Sigils And You", txt"Janet", pages.asJava)

        IO.pure(book)
