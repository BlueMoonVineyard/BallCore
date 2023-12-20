package BallCore.NoodleEditor

import BallCore.Storage.SQLManager
import BallCore.DataStructures.Clock
import skunk.implicits._
import BallCore.Storage.Migration
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID
import skunk.Session
import cats.effect.IO
import org.bukkit.plugin.Plugin
import skunk.codec.all._
import cats.data.OptionT
import java.time.temporal.ChronoUnit
import BallCore.TextComponents._

class EssenceListener(using eg: EssenceGiver, sql: SQLManager, p: Plugin)
    extends Listener:
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onJoin(event: PlayerJoinEvent): Unit =
        sql.useFireAndForget(
            sql.withS(
                eg.join(event.getPlayer.getUniqueId)
            ).ifM(
                IO {
                    val plr = event.getPlayer
                    plr.getScheduler()
                        .runDelayed(
                            p,
                            _ => {
                                val is = Essence.template.clone()
                                is.setAmount(4)
                                plr.sendServerMessage(
                                    txt"You've gotten your daily essence!"
                                )
                                plr.getInventory.addItem(is).forEach {
                                    (_, is) =>
                                        val _ = plr.getWorld
                                            .dropItemNaturally(
                                                plr.getLocation,
                                                is,
                                            )
                                }
                                sql.useFireAndForget(
                                    sql.withS(eg.give(plr.getUniqueId))
                                )
                            },
                            () => (),
                            10 * 60 * 20,
                        )
                },
                IO.pure(()),
            )
        )

class EssenceGiver()(using sql: SQLManager, c: Clock):
    sql.applyMigration(
        Migration(
            "Initial Essence Giver",
            List(
                sql"""
                CREATE TABLE EssenceGiver (
                    Player UUID PRIMARY KEY,
                    LastGaveEssenceAt TIMESTAMPTZ NOT NULL
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE EssenceGiver;
                """.command
            ),
        )
    )

    def give(player: UUID)(using Session[IO]): IO[Unit] =
        c.nowIO().flatMap { now =>
            sql.commandIO(
                sql"""
            INSERT INTO EssenceGiver (
                Player, LastGaveEssenceAt
            ) VALUES (
                $uuid, $timestamptz
            ) ON CONFLICT (Player) DO UPDATE
                SET LastGaveEssenceAt = EXCLUDED.LastGaveEssenceAt;
            """,
                (player, now),
            ).map(_ => ())
        }

    def join(player: UUID)(using Session[IO]): IO[Boolean] =
        (for
            originalTime <- OptionT(
                sql.queryOptionIO(
                    sql"""
            SELECT LastGaveEssenceAt FROM EssenceGiver WHERE Player = $uuid
            """,
                    timestamptz,
                    player,
                )
            )
            nowTime <- OptionT.liftF(c.nowIO())
            distance = ChronoUnit.HOURS.between(originalTime, nowTime)
        yield distance >= 24).getOrElse(true)
