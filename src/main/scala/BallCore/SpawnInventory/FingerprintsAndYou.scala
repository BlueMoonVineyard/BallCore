package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player
import cats.effect.IO
import net.kyori.adventure.inventory.Book

object FingerprintsAndYou:
    val builder = MiniMessage.builder().strict(true).build()
    def viewForPlayer(plr: Player): IO[Book] =
      val pages =
          List(
              s"""Fingerprints And You
                 |
                 |<b>Table of Contents</b>
                 |- <link:2>Fingerprint IDs</link>
                 |- <link:4>Retrieving Fingerprints</link>
                 |- <link:5>When Fingerprints Spawn</link>""".stripMargin,
              s"""<b>Fingerprint IDs</b>
                 |Each player has a unique fingerprint ID that they can examine with <#00d485>/fingerprint</#00d485>.
                 |Fingerprints only record this ID, not the player name.""".stripMargin,
              s"""Players will need to maintain their own databases of fingerprint IDs to player names for investigative purposes.""".stripMargin,
              s"""<b>Retrieving Fingerprints</b>
                 |Using a Brush around an area will allow you to retrieve nearby fingerprints.
                 |These will be compiled in a book.""".stripMargin,
              s"""<b>When Fingerprints Spawn</b>
                 |Fingerprints are left behind when a player busts through a beacon's protective area.""".stripMargin,
          ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

      val book = Book
          .book(txt"Fingerprints And You", txt"Janet", pages.asJava)

      IO.pure(book)
