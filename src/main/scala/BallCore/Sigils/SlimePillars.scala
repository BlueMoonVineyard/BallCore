// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Sigils

import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import scala.util.chaining._
import BallCore.Storage
import BallCore.Beacons.CivBeaconManager
import skunk.implicits._
import skunk.codec.all._
import org.bukkit.entity.Interaction
import skunk.Session
import cats.effect.IO
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
					BattleID UUID NOT NULL,
					InteractionEntityID UUID NOT NULL,
					Health INTEGER NOT NULL,
					UNIQUE(InteractionEntityID),
					FOREIGN KEY (InteractionEntityID) REFERENCES CustomEntities(InteractionEntityID) ON DELETE CASCADE,
                    FORIEGN KEY (BattleID) REFERENCES Battles(BattleID)
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

    def addPillar(interaction: Interaction, battle: BattleID)(using
        Session[IO]
    ): IO[Unit] =
        sql.commandIO(
            sql"""
		INSERT INTO SlimePillars (
			BattleID, InteractionEntityID, Health
		) VALUES (
			$uuid, $uuid, 60
		)
		""",
            (battle, interaction.getUniqueId()),
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
