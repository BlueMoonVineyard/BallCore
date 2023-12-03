package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player
import cats.effect.IO
import net.kyori.adventure.inventory.Book

object WorkstationsAndYou:
    val builder = MiniMessage.builder().strict(true).build()
    def viewForPlayer(plr: Player): IO[Book] =
      val pages =
          List(
              s"""Workstations And You
                 |
                 |<b>Table of Contents</b>
                 |- <link:2>Station Maker</link>
                 |- <link:3>Basic Operations</link>
                 |- <link:4>Work</link>""".stripMargin,
              s"""<b>Station Maker</b>
                 |The Station Maker is your gateway into the wonderful world of workstations.
                 |To craft it, put crafting tables in a 2x2 crafting grid.""".stripMargin,
              s"""<b>Basic Operations</b>
                 |Right click a workstation to view the recipe selector.
                 |To start working a recipe, right-click it.
                 |Workstations pull ingredients and put results into an adjacent chest.""".stripMargin,
              s"""<b>Work</b>
                 |Recipes require work to be made: every second a player is making a recipe, they contribute 1 work per second.
                 |The more players are working, the faster recipes are made.
                 |Some recipes require the work of more than one player.""".stripMargin,
          ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

      val book = Book
          .book(txt"Workstations And You", txt"Janet", pages.asJava)

      IO.pure(book)
