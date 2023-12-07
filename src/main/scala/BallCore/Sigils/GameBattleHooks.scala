package BallCore.Sigils

import BallCore.Beacons.BeaconID
import cats.effect.IO
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Geometry
import java.util.UUID
import org.locationtech.jts.geom.GeometryFactory
import org.bukkit.Bukkit
import scala.concurrent.ExecutionContext
import BallCore.Folia.LocationExecutionContext
import org.bukkit.Location
import org.locationtech.jts.shape.random.RandomPointsBuilder
import org.bukkit.plugin.Plugin
import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.EntityType
import scala.util.chaining._
import org.bukkit.util.Transformation
import org.joml.Vector3f
import org.joml.AxisAngle4f
import org.bukkit.entity.Interaction
import skunk.Session
import BallCore.Beacons.CivBeaconManager
import org.locationtech.jts.geom.Coordinate
import BallCore.WebHooks.WebHookManager
import BallCore.Groups.GroupManager
import skunk.Transaction
import BallCore.TextComponents._
import cats.effect.Temporal
import scala.concurrent.duration._
import scala.collection.concurrent.TrieMap
import cats.effect.kernel.Deferred
import BallCore.Groups.GroupID
import cats.effect.kernel.Resource
import cats.effect.kernel.MonadCancel
import cats.data.OptionT

object GameBattleHooks:
    def register()(using
        Plugin,
        CustomEntityManager,
        SlimePillarManager,
        CivBeaconManager,
        WebHookManager,
        GroupManager,
    ): GameBattleHooks =
        GameBattleHooks()

extension (i: IO.type)
    def on[A](ec: ExecutionContext)(thunk: => A): IO[A] =
        IO(thunk).evalOn(ec)

class GameBattleHooks(using
    p: Plugin,
    cem: CustomEntityManager,
    spm: SlimePillarManager,
    cbm: CivBeaconManager,
    webhooks: WebHookManager,
    gm: GroupManager,
) extends BattleHooks:
    private val gf = GeometryFactory()
    private val cancellations = TrieMap[GroupID, Deferred[IO, Unit]]()

    private def spawnPillarAt(block: Block, battle: BattleID)(using
        Session[IO]
    ): IO[Unit] =
        for {
            entities <- IO.on(LocationExecutionContext(block.getLocation())) {
                val world = block.getWorld()

                val targetXZ =
                    block.getLocation().clone().tap(_.add(0.5, 1, 0.5))

                val targetModelLocation = targetXZ
                    .clone()
                    .tap(
                        _.add(
                            0,
                            SlimePillar.scale - SlimePillar.heightBlocks,
                            0,
                        )
                    )
                val itemDisplay = world
                    .spawnEntity(targetModelLocation, EntityType.ITEM_DISPLAY)
                    .asInstanceOf[ItemDisplay]
                val scale = SlimePillar.scale
                itemDisplay.setTransformation(
                    Transformation(
                        Vector3f(),
                        AxisAngle4f(),
                        Vector3f(scale.toFloat),
                        AxisAngle4f(),
                    )
                )
                itemDisplay.setItemStack(SlimePillar.slimePillarModel)

                val interaction = world
                    .spawnEntity(targetXZ, EntityType.INTERACTION)
                    .asInstanceOf[Interaction]
                interaction.setInteractionHeight(
                    SlimePillar.totalHeightBlocks.toFloat
                )
                interaction.setInteractionWidth(
                    SlimePillar.heightBlocks.toFloat
                )
                interaction.setResponsive(true)

                (interaction, itemDisplay)
            }
            _ <- cem.addEntity(entities._1, entities._2, SlimePillar.entityKind)
            _ <- spm.addPillar(entities._1, battle)
        } yield ()

    private def randomPointIn(area: Geometry): IO[Coordinate] =
        IO {
            val builder = RandomPointsBuilder(gf)
            builder.setExtent(area)
            builder.setNumPoints(1)
            builder.getGeometry().getCoordinates().head
        }

    private def highestBlockAt(worldID: UUID, point: Coordinate): IO[Block] =
        val world = Bukkit.getWorld(worldID)
        IO.on(
            LocationExecutionContext(
                Location(world, point.getX(), 0, point.getY())
            )
        ) {
            world.getHighestBlockAt(point.getX().toInt, point.getY().toInt)
        }

    override def spawnPillarFor(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        contestedArea: Geometry,
        worldID: UUID,
        defensiveBeacon: BeaconID,
        pillarWasDefended: Option[Boolean],
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for {
            point <- randomPointIn(contestedArea)
            block <- highestBlockAt(worldID, point)
            _ <- spawnPillarAt(block, battle)
            _ <- pillarWasDefended match
                case (Some(x)) =>
                    for {
                        offensiveGroupID <- cbm
                            .getGroup(offensiveBeacon)
                            .map(_.get)
                        offensiveName <- gm
                            .getGroup(offensiveGroupID)
                            .map(_.metadata.name)
                            .value
                            .map(_.toOption.get)
                        defensiveGroupID <- cbm
                            .getGroup(defensiveBeacon)
                            .map(_.get)
                        defensiveName <- gm
                            .getGroup(defensiveGroupID)
                            .map(_.metadata.name)
                            .value
                            .map(_.toOption.get)

                        offensiveMessage =
                            if x then
                                (
                                    (name: String) =>
                                        txt"[$name] A pillar was lost to the defense in the battle against $defensiveName!",
                                    s"A pillar was lost to the defense in the battle against $defensiveName!",
                                )
                            else
                                (
                                    (name: String) =>
                                        txt"[$name] A pillar was taken from the defense in the battle against $defensiveName!",
                                    s"A pillar was taken from the defense in the battle against $defensiveName!",
                                )

                        defensiveMessage =
                            if x then
                                (
                                    (name: String) =>
                                        txt"[$name] A pillar was defended against the offense in the battle against $offensiveName!",
                                    s"A pillar was defended against the offense in the battle against $offensiveName!",
                                )
                            else
                                (
                                    (name: String) =>
                                        txt"[$name] A pillar was lost to the offense in the battle against $offensiveName!",
                                    s"A pillar was lost to the offense in the battle against $offensiveName!",
                                )

                        _ <- gm
                            .groupAudience(offensiveGroupID)
                            .semiflatTap { (name, audience) =>
                                IO {
                                    audience.sendServerMessage(
                                        offensiveMessage._1(name)
                                    )
                                }
                            }
                            .value
                        _ <- webhooks.broadcastTo(
                            offensiveGroupID,
                            offensiveMessage._2,
                        )
                        _ <- gm
                            .groupAudience(defensiveGroupID)
                            .semiflatTap { (name, audience) =>
                                IO {
                                    audience.sendServerMessage(
                                        defensiveMessage._1(name)
                                    )
                                }
                            }
                            .value
                        _ <- webhooks.broadcastTo(
                            defensiveGroupID,
                            defensiveMessage._2,
                        )
                    } yield ()
                case _ =>
                    IO.pure(())
        } yield ()

    private def despawnPillarsFor(battle: BattleID)(using
        Session[IO]
    ): IO[Unit] =
        val _ = battle
        IO.unit // TODO: multiple slime pillars

    override def battleDefended(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for {
            offensiveGroupID <- cbm.getGroup(offensiveBeacon).map(_.get)
            offensiveName <- gm
                .getGroup(offensiveGroupID)
                .map(_.metadata.name)
                .value
                .map(_.toOption.get)
            defensiveGroupID <- cbm.getGroup(defensiveBeacon).map(_.get)
            defensiveName <- gm
                .getGroup(defensiveGroupID)
                .map(_.metadata.name)
                .value
                .map(_.toOption.get)

            _ <- gm
                .groupAudience(offensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] You lost the battle against $defensiveName! You didn't take land."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                offensiveGroupID,
                s"You lost the battle against $defensiveName! You didn't take land.",
            )
            _ <- gm
                .groupAudience(defensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] You won the battle against $offensiveName! You kept land."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                defensiveGroupID,
                s"You won the battle against $offensiveName! You kept land.",
            )

            _ <- despawnPillarsFor(battle)
        } yield ()

    override def battleTaken(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
        contestedArea: Polygon,
        desiredArea: Polygon,
        world: UUID,
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for {
            offensiveGroupID <- cbm.getGroup(offensiveBeacon).map(_.get)
            offensiveName <- gm
                .getGroup(offensiveGroupID)
                .map(_.metadata.name)
                .value
                .map(_.toOption.get)
            defensiveGroupID <- cbm.getGroup(defensiveBeacon).map(_.get)
            defensiveName <- gm
                .getGroup(defensiveGroupID)
                .map(_.metadata.name)
                .value
                .map(_.toOption.get)

            _ <- gm
                .groupAudience(offensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] You won the battle against $defensiveName! You took land."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                offensiveGroupID,
                s"You won the battle against $defensiveName! You took land.",
            )
            _ <- gm
                .groupAudience(defensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] You lost the battle against $offensiveName! You lost land."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                defensiveGroupID,
                s"You lost the battle against $offensiveName! You lost land.",
            )

            world <- IO { Bukkit.getWorld(world) }
            oldDefensiveArea <- cbm.getPolygonFor(defensiveBeacon)
            _ <- cbm.sudoSetBeaconPolygon(
                defensiveBeacon,
                world,
                oldDefensiveArea.get
                    .buffer(0)
                    .difference(contestedArea.buffer(0))
                    .asInstanceOf[Polygon],
            )
            _ <- cbm.sudoSetBeaconPolygon(offensiveBeacon, world, desiredArea)
            _ <- despawnPillarsFor(battle)
        } yield ()

    private def cancellationFor(group: GroupID): IO[Unit] =
        Resource
            .make(for {
                deferred <- Deferred[IO, Unit]
                _ <- IO { cancellations(group) = deferred }
            } yield deferred)(a =>
                IO { val _ = cancellations.remove(group, a) }
            )
            .use(_.get)

    def cancelImpendingBattle(as: GroupID): IO[Unit] =
        OptionT(IO {
            cancellations.get(as)
        }).flatMap { deferred =>
            OptionT.liftF(deferred.complete(()))
        }.value
            .map(_ => ())

    override def impendingBattle(
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
        contestedArea: Geometry,
        world: UUID,
    )(using Session[IO], Transaction[IO]): IO[Unit] =
        for {
            offensiveGroupID <- cbm.getGroup(offensiveBeacon).map(_.get)
            offensiveName <- gm
                .getGroup(offensiveGroupID)
                .map(_.metadata.name)
                .value
                .map(_.toOption.get)
            defensiveGroupID <- cbm.getGroup(defensiveBeacon).map(_.get)
            defensiveName <- gm
                .getGroup(defensiveGroupID)
                .map(_.metadata.name)
                .value
                .map(_.toOption.get)

            _ <- gm
                .groupAudience(offensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] You are about to start a battle with $defensiveName! Anyone with the correct permissions can cancel this within 10 minutes with ${txt("/cancel-battle.")
                                    .color(Colors.teal)}"
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                offensiveGroupID,
                s"You are about to start a battle with $defensiveName! Anyone with the correct permissions can log on and cancel this within 10 minutes with `/cancel-battle`.",
            )

            result <- IO.race(
                Temporal[IO].sleep(10.minutes),
                cancellationFor(offensiveGroupID),
            )

            _ <- result match
                case Left(_) =>
                    IO.pure(())
                case Right(_) =>
                    for {
                        _ <- gm
                            .groupAudience(offensiveGroupID)
                            .semiflatTap { (name, audience) =>
                                IO {
                                    audience.sendServerMessage(
                                        txt"[$name] The battle with $defensiveName was called off!"
                                    )
                                }
                            }
                            .value
                        _ <- webhooks.broadcastTo(
                            offensiveGroupID,
                            s"The battle with $defensiveName was called off!",
                        )
                        _ <- MonadCancel[IO].canceled
                    } yield ()

            // declare to offense
            _ <- gm
                .groupAudience(offensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] The battle with $defensiveName is locked in! It will start within 20 minutes."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                offensiveGroupID,
                s"The battle with $defensiveName is locked in! It will start within 20 minutes.",
            )
            // declare to defense
            _ <- gm
                .groupAudience(defensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] $offensiveName is starting a battle with you! It will begin in 20 minutes."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                defensiveGroupID,
                s"$offensiveName is starting a battle with you! It will begin in 20 minutes.",
            )

            _ <- Temporal[IO].sleep(20.minutes)

            // declare to offense
            _ <- gm
                .groupAudience(offensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] The battle with $defensiveName is starting now!."
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                offensiveGroupID,
                s"The battle with $defensiveName is starting now!.",
            )
            // declare to defense
            _ <- gm
                .groupAudience(defensiveGroupID)
                .semiflatTap { (name, audience) =>
                    IO {
                        audience.sendServerMessage(
                            txt"[$name] The battle with $offensiveName is starting now!"
                        )
                    }
                }
                .value
            _ <- webhooks.broadcastTo(
                defensiveGroupID,
                s"The battle with $offensiveName is starting now!",
            )
        } yield ()
