package BallCore.Fingerprints

import BallCore.Storage.SQLManager
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.Material
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import BallCore.TextComponents._
import BallCore.DataStructures.Clock
import java.time.OffsetDateTime
import scala.jdk.CollectionConverters._
import BallCore.Datekeeping.Datekeeping
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.Plugin
import cats.effect.IO
import BallCore.Folia.EntityExecutionContext
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextDecoration.State
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority

def fingerprintsToBook(
    gatheredAt: OffsetDateTime,
    fingerprints: List[Fingerprint],
    player: Player,
): ItemStack =
    val date = Datekeeping.timeFrom(gatheredAt)
    val book = ItemStack(Material.WRITTEN_BOOK)
    val meta = book.getItemMeta().asInstanceOf[BookMeta]
    meta.title(txt"Fingerprints")
    meta.author(player.displayName())
    meta.lore(
        List(
            txt"Gathered on ${date.toDateString}",
            txt"at ${date.toTimeString}",
        ).map(
            _.color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, State.FALSE)
        ).asJava
    )
    meta.pages(fingerprints.map { fingerprint =>
        List(
            txt"Fingerprint".decorate(TextDecoration.BOLD),
            txt"Player ID: ${fingerprint.creator}",
            txt"Left On: ${date.toDateString} at ${date.toTimeString}",
            txt"Action: ${fingerprint.reason.explain}",
        ).mkComponent(txt("\n"))
    }.asJava)
    book.setItemMeta(meta)
    book

class Listener()(using
    sql: SQLManager,
    fm: FingerprintManager,
    c: Clock,
    p: Plugin,
) extends org.bukkit.event.Listener:
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onItemInteractBlock(event: PlayerInteractEvent): Unit =
        if !event.hasItem || event
                .getItem()
                .getType() != Material.BRUSH || event.getAction != Action.RIGHT_CLICK_BLOCK
        then return ()

        val block = event.getClickedBlock()

        sql.useFireAndForget(sql.withS(for {
            now <- c.nowIO()
            fprints <- fm.fingerprintsInTheVicinityOf(
                block.getX,
                block.getY,
                block.getZ,
                block.getWorld.getUID,
            )
            book = fingerprintsToBook(now, fprints, event.getPlayer())
            _ <-
                if fprints.size > 0 then
                    IO { event.getPlayer().getInventory().addItem(book) }
                        .evalOn(EntityExecutionContext(event.getPlayer()))
                else
                    IO {
                        event
                            .getPlayer()
                            .sendServerMessage(
                                txt"There are no fingerprints in your area..."
                            )
                    }
        } yield ()))
