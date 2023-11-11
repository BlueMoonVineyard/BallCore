// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Sigils

import BallCore.CustomItems.CustomItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import BallCore.TextComponents._
import scala.util.chaining._
import BallCore.Storage
import BallCore.Beacons.CivBeaconManager
import BallCore.CustomItems.CustomItem
import skunk.implicits._
import skunk.codec.all._
import org.bukkit.event.player.PlayerInteractEvent
import BallCore.CustomItems.Listeners
import org.bukkit.entity.Interaction
import BallCore.Beacons.BeaconID
import skunk.Session
import cats.effect.IO
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import org.joml.Vector3f
import org.joml.AxisAngle4f
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import BallCore.Folia.EntityExecutionContext
import scala.concurrent.ExecutionContext
import BallCore.Folia.FireAndForget
import scala.jdk.CollectionConverters._
import org.bukkit.entity.Player

object SlimePillar:
    val slimePillarModel = ItemStack(Material.STICK)
    slimePillarModel.setItemMeta(
        slimePillarModel.getItemMeta().tap(_.setCustomModelData(7))
    )

    val debugSpawnItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "slime_pillar_debug"),
        Material.PAPER,
        txt"Slime Pillar Debug",
    )
    debugSpawnItemStack.setItemMeta(
        debugSpawnItemStack.getItemMeta().tap(_.setCustomModelData(3))
    )

    val scale = 4.5
    val heightBlocks = (8.0 * scale) / 16.0
    val totalHeightBlocks = heightBlocks * 4

    val entityKind = NamespacedKey("ballcore", "slime_pillar")

class SlimePillarManager(using
    sql: Storage.SQLManager,
    cbm: CivBeaconManager,
    cem: CustomEntityManager,
):
    sql.applyMigration(
        Storage.Migration(
            "Initial Slime Pillar Manager",
            List(
                sql"""
				CREATE TABLE SlimePillars (
					BeaconID UUID NOT NULL,
					InteractionEntityID UUID NOT NULL,
					Health INTEGER NOT NULL,
					UNIQUE(InteractionEntityID),
					FOREIGN KEY (InteractionEntityID) REFERENCES CustomEntities(InteractionEntityID) ON DELETE CASCADE
				);
				""".command
            ),
            List(
                sql"""
				DROP TABLE SlimePillars;
				""".command
            ),
        )
    )
    val _ = cbm
    val _ = cem

    def addPillar(interaction: Interaction, beacon: BeaconID)(using
        Session[IO]
    ): IO[Unit] =
        sql.commandIO(
            sql"""
		INSERT INTO SlimePillars (
			BeaconID, InteractionEntityID
		) VALUES (
			$uuid, $uuid
		)
		""",
            (beacon, interaction.getUniqueId()),
        ).map(_ => ())

class SlimePillarFlinger()(using
    p: Plugin,
    sql: Storage.SQLManager,
    cem: CustomEntityManager,
):
    val randomizer = scala.util.Random()

    def doFlings(): Unit =
        sql.useBlocking(cem.entitiesOfKind(SlimePillar.entityKind)).foreach {
            entity =>
                val interaction = Bukkit
                    .getEntity(entity.interaction)
                    .asInstanceOf[Interaction]
                if interaction != null then
                    given ec: ExecutionContext =
                        EntityExecutionContext(interaction)
                    FireAndForget {
                        val players =
                            interaction
                                .getNearbyEntities(10, 3, 10)
                                .asScala
                                .flatMap(castOption[Player])
                        players
                            .filter(
                                _.getBoundingBox()
                                    .overlaps(interaction.getBoundingBox())
                            )
                            .foreach { plr =>
                                val velocity = interaction
                                    .getLocation()
                                    .toVector()
                                    .subtract(plr.getLocation().toVector())
                                    .setY(1)
                                    .normalize()
                                    .multiply(-2.0)
                                    .setY(1)
                                plr.setVelocity(velocity)
                            }
                    }
        }

    def doLooks(): Unit =
        sql.useBlocking(cem.entitiesOfKind(SlimePillar.entityKind)).foreach {
            entity =>
                val interaction = Bukkit
                    .getEntity(entity.interaction)
                    .asInstanceOf[Interaction]
                if interaction != null then
                    given ec: ExecutionContext =
                        EntityExecutionContext(interaction)
                    FireAndForget {
                        val players =
                            interaction
                                .getNearbyEntities(10, 3, 10)
                                .asScala
                                .flatMap(castOption[Player])
                        if players.length > 0 then
                            val player =
                                players(randomizer.nextInt(players.length))
                            val loc = interaction
                                .getLocation()
                                .clone()
                                .setDirection(
                                    player
                                        .getLocation()
                                        .clone()
                                        .subtract(interaction.getLocation())
                                        .toVector()
                                )
                            interaction.setRotation(loc.getYaw(), 0)
                            Bukkit
                                .getEntity(entity.display)
                                .setRotation(loc.getYaw(), 0)
                    }
        }

class SlimePillarDebugSpawnItemStack(using
    cem: CustomEntityManager,
    spm: SlimePillarManager,
    cbm: CivBeaconManager,
    sql: Storage.SQLManager,
) extends CustomItem,
      Listeners.ItemUsedOnBlock:
    def group = Sigil.group
    def template = SlimePillar.debugSpawnItemStack

    val _ = sql
    val _ = spm
    val _ = cbm
    val _ = cem

    override def onItemUsedOnBlock(event: PlayerInteractEvent): Unit =
        val beacon =
            sql.useBlocking(
                cbm.getBeaconFor(event.getPlayer().getUniqueId())
            ) match
                case None =>
                    import BallCore.UI.ChatElements._
                    event
                        .getPlayer()
                        .sendServerMessage(
                            txt"You must have a Civilization Heart placed to spawn a Pillar!"
                                .color(Colors.red)
                        )
                    event.setCancelled(true)
                    return
                case Some(value) =>
                    value

        val block = event.getClickedBlock()
        val world = block.getWorld()

        val targetXZ = block.getLocation().clone().tap(_.add(0.5, 1, 0.5))

        val targetModelLocation = targetXZ
            .clone()
            .tap(_.add(0, SlimePillar.scale - SlimePillar.heightBlocks, 0))
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
        interaction.setInteractionHeight(SlimePillar.totalHeightBlocks.toFloat)
        interaction.setInteractionWidth(SlimePillar.heightBlocks.toFloat)
        interaction.setResponsive(true)

        sql.useBlocking(
            cem.addEntity(interaction, itemDisplay, SlimePillar.entityKind)
        )
        sql.useBlocking(spm.addPillar(interaction, beacon))
