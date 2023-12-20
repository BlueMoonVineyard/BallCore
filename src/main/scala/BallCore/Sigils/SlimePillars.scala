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
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.Listener
import net.kyori.adventure.audience.Audience
import scala.collection.concurrent.TrieMap
import net.kyori.adventure.bossbar.BossBar
import java.time.OffsetDateTime
import BallCore.DataStructures.Clock
import java.time.temporal.ChronoUnit
import org.bukkit.Location
import org.bukkit.Particle
import skunk.Transaction
import scala.util.NotGiven

object SlimePillar:
    val slimePillarModel = ItemStack(Material.STICK)
    slimePillarModel.setItemMeta(
        slimePillarModel.getItemMeta().tap(_.setCustomModelData(7))
    )

    val scale = 4.5
    val heightBlocks = (8.0 * scale) / 16.0
    val totalHeightBlocks = heightBlocks * 4

    val entityKind = NamespacedKey("ballcore", "slime_pillar")

enum PillarResult:
    case remains(newHealth: Int)
    case finished

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

    private val bossBars = TrieMap[Interaction, (BossBar, List[Audience])]()
    private val currentlyObservingBars = TrieMap[Audience, BossBar]()

    private def updateBossBarFor(
        interaction: Interaction,
        health: Int,
    ): IO[BossBar] =
        IO {
            bossBars
                .updateWith(interaction) { bar =>
                    import BallCore.TextComponents._
                    bar match
                        case None =>
                            Some(
                                (
                                    BossBar.bossBar(
                                        txt"Slime Pillar",
                                        health.toFloat / 100.0f,
                                        BossBar.Color.PURPLE,
                                        BossBar.Overlay.NOTCHED_20,
                                    ),
                                    List(),
                                )
                            )
                        case Some(bar, audience) =>
                            Some(
                                (
                                    bar.progress(health.toFloat / 100.0f),
                                    audience,
                                )
                            )
                }
                .get
                ._1
        }
    private def removeBossBarFor(interaction: Interaction): IO[Unit] =
        IO {
            bossBars.remove(interaction) match
                case None =>
                case Some((bar, audience)) =>
                    audience.foreach(bar.removeViewer)
                    audience.foreach(currentlyObservingBars.remove)
        }
    private def showBossBarTo(
        interaction: Interaction,
        target: Audience,
    ): IO[Unit] =
        IO {
            val _ = bossBars.updateWith(interaction) {
                case Some((bar, audience)) if !audience.contains(target) =>
                    bar.addViewer(target)
                    val _ = currentlyObservingBars.updateWith(interaction) {
                        case Some(oldBar) =>
                            bar.removeViewer(target)
                            Some(bar)
                        case None => Some(bar)
                    }
                    Some((bar, target :: audience))
                case x => x
            }
        }

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

    def slapPillar(
        interaction: Interaction,
        player: Audience,
    )(using
        battleManager: BattleManager
    )(using Session[IO], NotGiven[Transaction[IO]]): IO[PillarResult] =
        for {
            health <- sql.queryUniqueIO(
                sql"""
            UPDATE SlimePillars SET Health = Health - 1 WHERE InteractionEntityID = $uuid RETURNING Health, BattleID;
            """,
                (int4 *: uuid),
                interaction.getUniqueId(),
            )
            result <-
                if health._1 < 1 then
                    for {
                        _ <- sql.commandIO(
                            sql"""
                        DELETE FROM SlimePillars WHERE InteractionEntityID = $uuid;
                        """,
                            interaction.getUniqueId(),
                        )
                        _ <- removeBossBarFor(interaction)
                        _ <- cem.deleteEntity(interaction)
                        _ <- battleManager.pillarTaken(health._2)
                    } yield PillarResult.finished
                else
                    for {
                        _ <- battleManager.showBossBarTo(health._2, player)
                        _ <- updateBossBarFor(interaction, health._1)
                        _ <- showBossBarTo(interaction, player)
                    } yield PillarResult.remains(health._1)
        } yield result

    def healPillar(
        interaction: Interaction,
        player: Audience,
    )(using
        battleManager: BattleManager
    )(using Session[IO], NotGiven[Transaction[IO]]): IO[PillarResult] =
        for {
            health <- sql.queryUniqueIO(
                sql"""
            UPDATE SlimePillars SET Health = Health + 1 WHERE InteractionEntityID = $uuid RETURNING Health, BattleID;
            """,
                (int4 *: uuid),
                interaction.getUniqueId(),
            )
            _ <- battleManager.showBossBarTo(health._2, player)
            result <-
                if health._1 > 100 then
                    for {
                        _ <- sql.commandIO(
                            sql"""
                        DELETE FROM SlimePillars WHERE InteractionEntityID = $uuid;
                        """,
                            interaction.getUniqueId(),
                        )
                        _ <- removeBossBarFor(interaction)
                        _ <- cem.deleteEntity(interaction)
                        _ <- battleManager.pillarDefended(health._2)
                    } yield PillarResult.finished
                else
                    for {
                        _ <- battleManager.showBossBarTo(health._2, player)
                        _ <- updateBossBarFor(interaction, health._1)
                        _ <- showBossBarTo(interaction, player)
                    } yield PillarResult.remains(health._1)
        } yield result

class SlimePillarSlapDetector()(using
    sql: Storage.SQLManager,
    cem: CustomEntityManager,
    spm: SlimePillarManager,
    battleManager: BattleManager,
    clock: Clock,
) extends Listener:
    val lastSlaps = TrieMap[Player, OffsetDateTime]()

    def playEffect(at: Location, kind: Particle, offset: Double): Unit =
        at.getWorld
            .spawnParticle(
                kind,
                at,
                15,
                offset,
                offset,
                offset,
                0.4,
                null,
            )

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onSlap(event: EntityDamageByEntityEvent): Unit =
        if !event.getEntity().isInstanceOf[Interaction] then return
        if !event.getDamager().isInstanceOf[Player] then return

        val plr = event.getDamager().asInstanceOf[Player]
        val time = lastSlaps.getOrElseUpdate(
            event.getDamager().asInstanceOf[Player],
            clock.now(),
        )
        if ChronoUnit.MILLIS.between(time, clock.now()) < 750 then return
        lastSlaps.update(plr, clock.now())

        val intr = event.getEntity().asInstanceOf[Interaction]

        val result = plr.rayTraceEntities(10)
        if result == null then return
        val position = result.getHitPosition()
        val location = position.toLocation(plr.getWorld(), 0, 0)

        val isPillar = sql.useBlocking(
            sql.withS(cem.entityKind(intr))
        ) match
            case Some((ent, _)) if ent == SlimePillar.entityKind => true
            case _ => false

        if !isPillar then return

        playEffect(location, Particle.VILLAGER_ANGRY, 0.5)
        sql.useFireAndForget(
            sql.withS(spm.slapPillar(intr, plr))
        )

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onUnslap(event: PlayerInteractEntityEvent): Unit =
        if !event.getRightClicked().isInstanceOf[Interaction] then return

        val intr = event.getRightClicked().asInstanceOf[Interaction]

        val time = lastSlaps.getOrElseUpdate(event.getPlayer(), clock.now())
        if ChronoUnit.MILLIS.between(time, clock.now()) < 750 then return
        lastSlaps.update(event.getPlayer(), clock.now())

        val result = event.getPlayer().rayTraceEntities(10)
        if result == null then return
        val position = result.getHitPosition()
        val location = position.toLocation(event.getPlayer().getWorld(), 0, 0)

        val isPillar = sql.useBlocking(
            sql.withS(cem.entityKind(intr))
        ) match
            case Some((ent, _)) if ent == SlimePillar.entityKind => true
            case _ => false

        if !isPillar then return

        playEffect(location, Particle.HEART, 0.5)
        sql.useFireAndForget(
            sql.withS(spm.healPillar(intr, event.getPlayer()))
        )

class SlimePillarFlinger()(using
    p: Plugin,
    sql: Storage.SQLManager,
    cem: CustomEntityManager,
):
    val randomizer = scala.util.Random()

    def doFlings(): Unit =
        sql.useBlocking(sql.withS(cem.entitiesOfKind(SlimePillar.entityKind)))
            .foreach { entity =>
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
        sql.useBlocking(sql.withS(cem.entitiesOfKind(SlimePillar.entityKind)))
            .foreach { entity =>
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
