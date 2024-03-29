package BallCore.RandomSpawner

import BallCore.Storage.SQLManager
import BallCore.TextComponents._
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.PlayerDeathEvent
import cats.effect.IO
import org.bukkit.Bukkit

object Listener:
    def register()(using rs: RandomSpawn, sql: SQLManager, p: Plugin): Unit =
        p.getServer().getPluginManager().registerEvents(Listener(), p)

class Listener(using rs: RandomSpawn, sql: SQLManager, p: Plugin)
    extends org.bukkit.event.Listener:

    // THIS IS HACKY AS SHIT BUT FOLIA DOESNT FIRE THAT PLAYERRESPAWNEVENT
    // TORMENT
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onPlayerDied(event: PlayerDeathEvent): Unit =
        val player = event.getPlayer()

        val _ = player
            .getScheduler()
            .runAtFixedRate(
                p,
                task => {
                    if player.getHealth() > 0 then
                        if player.getBedSpawnLocation() == null then
                            sql.useFireAndForget(for {
                                block <- rs.randomSpawnLocation
                                _ <- IO.fromCompletableFuture(IO {
                                    player.teleportAsync(block.getLocation())
                                })
                                _ <- IO {
                                    player.sendServerMessage(
                                        txt"You've woken up in an unfamiliar place..."
                                    )
                                }
                            } yield ())
                        val _ = task.cancel()
                },
                () => (),
                1,
                1,
            )

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onPlayerJoin(event: PlayerJoinEvent): Unit =
        val player = event.getPlayer()
        if player.hasPlayedBefore() then return ()
        sql.useFireAndForget(for {
            block <- rs.randomSpawnLocation
            _ <- IO.fromCompletableFuture(IO {
                Bukkit
                    .getServer()
                    .sendMessage(
                        txt"Welcome ${player.displayName} to CivCubed!"
                            .color(Colors.teal)
                    )
                player.teleportAsync(block.getLocation())
            })
        } yield ())
