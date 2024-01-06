package BallCore.Commands

import BallCore.Acclimation.{AcclimationActor, AcclimationMessage, Information}
import BallCore.CustomItems.ItemRegistry
import BallCore.Plants.{PlantBatchManager, PlantMsg}
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import dev.jorel.commandapi.arguments.NamespacedKeyArgument
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.command.CommandSender
import dev.jorel.commandapi.arguments.PlayerArgument
import cats.effect.IO
import dev.jorel.commandapi.executors.CommandExecutor
import BallCore.RandomSpawner.RandomSpawn
import BallCore.SpawnInventory.InventorySetter
import dev.jorel.commandapi.arguments.IntegerArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import BallCore.NoodleEditor.EssenceDrainer

class CheatCommand(using
    registry: ItemRegistry,
    pbm: PlantBatchManager,
    aa: AcclimationActor,
    storage: BallCore.Acclimation.Storage,
    sql: SQLManager,
    rs: RandomSpawn,
    drainer: EssenceDrainer,
):
    import scala.jdk.CollectionConverters._

    val node =
        CommandTree("cheat")
            .withRequirement(_.hasPermission("ballcore.cheat"))
            .`then`(
                LiteralArgument("spawn")
                    .`then`(
                        NamespacedKeyArgument("item")
                            .replaceSuggestions(
                                ArgumentSuggestions.strings(
                                    registry
                                        .items()
                                        .map(item => item.id.toString())
                                        .asJava
                                )
                            )
                            .executesPlayer({ (sender, args) =>
                                registry.lookup(
                                    args.getUnchecked[NamespacedKey]("item")
                                ) match
                                    case None =>
                                        sender.sendServerMessage(
                                            txt"Unknown item"
                                        )
                                    case Some(item) =>
                                        val is = item.template.clone()
                                        sender.getInventory.addItem(is)
                                        sender.sendServerMessage(
                                            txt"Gave 1 item"
                                        )
                            }: PlayerCommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("spawn-everything")
                    .executesPlayer({ (sender, args) =>
                        registry.items().foreach { item =>
                            val is = item.template.clone()
                            sender.getInventory.addItem(is).forEach { (_, is) =>
                                val _ = sender.getWorld
                                    .dropItemNaturally(sender.getLocation, is)
                            }
                            sender.sendServerMessage(
                                txt"Gave 1 item"
                            )
                        }
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("eat-noodle-essence")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for
                            _ <- sql.withS(sql.withTX(drainer.drainEssence))
                            _ <- IO {
                                sender.sendServerMessage(txt"Drained essence!")
                            }
                        yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("spawn-inventory")
                    .executesPlayer({ (sender, args) =>
                        InventorySetter.giveSpawnInventory(sender)
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("spawnbook")
                    .executesPlayer({ (sender, args) =>
                        sender.openBook(BallCore.SpawnInventory.Book.book)
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("random-spawn")
                    .`then`(
                        PlayerArgument("player")
                            .executes({ (sender, args) =>
                                val target = args.getUnchecked[Player]("player")
                                sql.useFireAndForget(
                                    rs.randomSpawnLocation.flatMap { location =>
                                        IO {
                                            target.teleportAsync(
                                                location.getLocation()
                                            )
                                        }
                                    }
                                )
                            }: CommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("tick-plants")
                    .`then`(
                        IntegerArgument("amount")
                            .executesPlayer({ (sender, args) =>
                                for _ <- 1 to args
                                        .getUnchecked[Integer]("amount")
                                        .intValue()
                                do
                                    pbm.send(PlantMsg.tickPlants)
                                    sender.sendServerMessage(
                                        txt"An hour of ingame time has passed"
                                    )
                            }: PlayerCommandExecutor)
                    )
            )
            .`then`(
                LiteralArgument("tick-acclimation")
                    .executesPlayer({ (sender, args) =>
                        aa.send(AcclimationMessage.tick)
                        sender.sendServerMessage(
                            txt"Six hours of ingame time have passed"
                        )
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("my-acclimation")
                    .executesPlayer({ (sender, args) =>
                        val plr = sender.asInstanceOf[Player]
                        val uuid = sender.asInstanceOf[Player].getUniqueId
                        val (aElevation, aLatitude, aLongitude, aTemperature) =
                            sql.useBlocking(sql.withS(for {
                                elevation <- storage.getElevation(uuid)
                                latitude <- storage.getLatitude(uuid)
                                longitude <- storage.getLongitude(uuid)
                                temperature <- storage.getTemperature(uuid)
                            } yield (elevation, latitude, longitude, temperature)))
                        import Information.*
                        sender.sendServerMessage(
                            txt"Your current elevation: ${elevation(plr.getLocation().getY.toInt).toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted elevation: ${aElevation.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )
                        val (lat, long) =
                            latLong(
                                plr.getLocation().getX,
                                plr.getLocation().getZ,
                            )
                        sender.sendServerMessage(
                            txt"Your current latitude: ${lat.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted latitude: ${aLatitude.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )
                        sender.sendServerMessage(
                            txt"Your current longitude: ${lat.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted longitude: ${aLatitude.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )
                        val temp = temperature(
                            plr.getLocation().getX.toInt,
                            plr.getLocation().getY.toInt,
                            plr.getLocation().getZ.toInt,
                        )
                        sender.sendServerMessage(
                            txt"Your current temperature: ${temp.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)} | Your adapted temperature: ${aTemperature.toComponent
                                    .style(NamedTextColor.GOLD, TextDecoration.BOLD)}"
                        )

                        val dlat = Information.similarityNeg(lat, aLatitude)
                        val dlong = Information.similarityNeg(long, aLongitude)

                        // multiplier of the bonus on top of baseline rate
                        val bonusRateMultiplier = (dlat + dlong) / 2.0
                        sender.sendServerMessage(
                            txt"Your bonus rate multiplier for mining: ${bonusRateMultiplier.toComponent
                                    .style(NamedTextColor.GOLD)}"
                        )
                    }: PlayerCommandExecutor)
            )
