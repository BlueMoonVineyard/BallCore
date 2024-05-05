package CivCubed.Beacons

import CivCubed.Fingerprints.FingerprintManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerMoveEvent
import CivCubed.Storage.SQLManager
import cats.effect.IO
import CivCubed.Fingerprints.FingerprintReason
import CivCubed.Groups.GroupID
import org.bukkit.Location
import cats.data.OptionT
import cats.syntax.all.*
import CivCubed.Groups.GroupManager
import skunk.Session
import skunk.Transaction
import scala.util.Random
import CivCubed.TextComponents.*
import CivCubed.WebHooks.WebHookManager

class Listener()(using
    gm: GroupManager,
    cbm: CivBeaconManager,
    fingerprints: FingerprintManager,
    webhooks: WebHookManager,
    sql: SQLManager,
) extends org.bukkit.event.Listener:
    def notify(using Session[IO])(where: Location)(group: GroupID): IO[Unit] =
        IO(Random.between(0d, 1d))
            .map(_ < 0.5)
            .ifM(
                for
                    coords <- IO {
                        val xOffset =
                            Random.between(-3, 3)
                        val zOffset =
                            Random.between(-3, 3)
                        val x = where.getBlockX() + xOffset
                        val z = where.getBlockZ() + zOffset
                        (x, z)
                    }
                    audience <- sql.withTX(
                        gm
                            .groupAudience(group)
                            .value
                    )
                    _ <- IO {
                        audience.foreach((name, aud) =>
                            aud.sendServerMessage(
                                txt"[$name] Someone walked through your beacon border approximately around ${coords._1} ± 3 / ${where.getBlockY} / ${coords._2} ± 3"
                            )
                        )
                    }.flatMap { _ =>
                        sql.withTX(
                            webhooks.broadcastTo(
                                group,
                                s"Someone busted through your beacon approximately around ${coords._1} ± 3 / ${where.getBlockY} / ${coords._2} ± 3",
                            )
                        )
                    }
                yield (),
                IO.unit,
            )

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    def onPlayerMove(event: PlayerMoveEvent): Unit =
        event.getTo()
        sql.useFireAndForget(
            sql.withS(
                for
                    from <- cbm.beaconContaining(event.getFrom())
                    to <- cbm.beaconContaining(event.getTo())
                    _ <- IO
                        .pure(from != to)
                        .ifM(
                            for
                                _ <- sql.withTX(
                                    fingerprints.storeFingerprintAt(
                                        event.getTo.getX.toInt,
                                        event.getTo.getY.toInt,
                                        event.getTo.getZ.toInt,
                                        event.getTo.getWorld.getUID,
                                        event.getPlayer.getUniqueId,
                                        FingerprintReason.crossed,
                                    )
                                )
                                _ <- OptionT
                                    .fromOption[IO](to)
                                    .flatMapF(cbm.getGroup)
                                    .flatTap(x =>
                                        OptionT.liftF(notify(event.getTo)(x))
                                    )
                                    .value
                                _ <- OptionT
                                    .fromOption[IO](from)
                                    .flatMapF(cbm.getGroup)
                                    .flatTap(x =>
                                        OptionT.liftF(notify(event.getFrom)(x))
                                    )
                                    .value
                            yield (),
                            IO.unit,
                        )
                yield ()
            )
        )
