package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player
import cats.effect.IO
import net.kyori.adventure.inventory.Book

object BattlesAndYou:
    val builder = MiniMessage.builder().strict(true).build()
    def viewForPlayer(plr: Player): IO[Book] =
        val pages =
            List(
                s"""Battles And You
                   |
                   |<b>Table of Contents</b>
                   |- <link:2>The Basics</link>
                   |- <link:3>Starting Battles</link>
                   |- <link:4>Slime Pillars</link>
                   |- <link:5>Battle HP</link>
                   |- <link:6>Transfer of Land</link>""".stripMargin,
                s"""<b>The Basics</b>
                   |Battles are the process of taking over land. One party aims to take it and one aims to keep it.
                   |They are essentially a game of tug-of-war: smaller tug-of-wars with the Slime Pillars lead to the tug-of-war of the overall battle.""".stripMargin,
                s"""<b>Starting Battles</b>
                   |Attempt to claim some of your neighbours' land with a beacon, and you'll be prompted to declare a battle.
                   |If you do, you'll have 10 minutes to change your mind. Afterwards, both you and your enemy will be warned and the battle will start 20 minutes later.""".stripMargin,
                s"""<b>Slime Pillars</b>
                   |Slime pillars will spawn around the contested area.
                   |Defenders should right click them, and attackers should left click them.
                   |If a slime pillar's HP hits zero, the battle HP decreases. If it hits 100, the battle HP increases.""".stripMargin,
                s"""<b>Battle HP</b>
                   |If the battle HP decreases to 0, the attackers win.
                   |If it hits 100, the defenders win.""".stripMargin,
                s"""<b>Transfer of Land</b>
                   |If the attackers win, they gain the land that they initially sought out to get.""".stripMargin,
            ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

        val book = Book
            .book(txt"Battles And You", txt"Janet", pages.asJava)

        IO.pure(book)
