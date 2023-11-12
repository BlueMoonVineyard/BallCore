// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Sigils

import BallCore.Beacons.{BeaconID, CivBeaconManager}
import BallCore.CustomItems.{CustomItem, CustomItemStack, Listeners}
import BallCore.Folia.{EntityExecutionContext, FireAndForget}
import BallCore.Groups.UserID
import BallCore.Storage
import BallCore.UI.Elements.*
import cats.effect.IO
import org.bukkit.entity.*
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.bukkit.{Bukkit, Material, NamespacedKey}
import org.joml.{AxisAngle4f, Vector3f}
import skunk.Session
import skunk.codec.all.*
import skunk.implicits.*

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

object Slimes:
  val sigilSlime = ItemStack(Material.STICK)
  sigilSlime.setItemMeta(sigilSlime.getItemMeta().tap(_.setCustomModelData(6)))
  val slimeEggStack = CustomItemStack.make(
    NamespacedKey("ballcore", "sigil_slime_egg"),
    Material.PAPER,
    txt"Sigil Slime Egg"
  )
  slimeEggStack.setItemMeta(
    slimeEggStack.getItemMeta().tap(_.setCustomModelData(3))
  )

  val slimeScale = 1.5
  val heightBlocks = (8.0 * slimeScale) / 16.0

  val entityKind = NamespacedKey("ballcore", "sigil_slime")

case class EntityIDPair(interaction: UUID, display: UUID)

class SigilSlimeManager(using
                        sql: Storage.SQLManager,
                        cbm: CivBeaconManager,
                        ccm: CustomEntityManager
                       ):
  sql.applyMigration(
    Storage.Migration(
      "Initial Sigil Slime Manager",
      List(
        sql"""
				CREATE TABLE SigilSlimes (
					BanishedUserID UUID,
					BeaconID UUID NOT NULL,
					InteractionEntityID UUID NOT NULL,
					UNIQUE(BanishedUserID, BeaconID),
					UNIQUE(InteractionEntityID),
					FOREIGN KEY (BeaconID) REFERENCES CivBeacons(ID) ON DELETE CASCADE,
					FOREIGN KEY (InteractionEntityID) REFERENCES CustomEntities(InteractionEntityID) ON DELETE CASCADE
				);
				""".command
      ),
      List(
        sql"""
				DROP TABLE SigilSlimes;
				""".command
      )
    )
  )
  val _ = cbm
  val _ = ccm

  def addSlime(entity: UUID, beacon: BeaconID)(using Session[IO]): IO[Unit] =
    sql
      .commandIO(
        sql"""
		INSERT INTO SigilSlimes (
			BeaconID, InteractionEntityID
		) VALUES (
			$uuid, $uuid
		)
		""",
        (beacon, entity)
      )
      .map(_ => ())

  def banishedUsers(from: BeaconID)(using Session[IO]): IO[List[UserID]] =
    sql.queryListIO(
      sql"""
		SELECT BanishedUserID FROM SigilSlimes WHERE BeaconID = $uuid AND BanishedUserID IS NOT NULL;
		""",
      uuid,
      from
    )

  def isBanished(user: UserID, from: BeaconID)(using Session[IO]): IO[Boolean] =
    sql.queryUniqueIO(
      sql"""
		SELECT EXISTS (
			SELECT 1 FROM SigilSlimes WHERE BanishedUserID = $uuid AND BeaconID = $uuid
		);
		""",
      bool,
      (user, from)
    )

  def banish(user: UserID, slime: UUID)(using Session[IO]): IO[Unit] =
    sql
      .commandIO(
        sql"""
		UPDATE SigilSlimes
		SET
			BanishedUserID = $uuid
		WHERE
			InteractionEntityID = $uuid;
		""",
        (user, slime)
      )
      .map(_ => ())

  def unbanish(user: UserID, slime: UUID)(using Session[IO]): IO[Unit] =
    sql
      .commandIO(
        sql"""
		UPDATE SigilSlimes
		SET
			BanishedUserID = NULL
		WHERE
			InteractionEntityID = $uuid AND
			BanishedUserID = $uuid;
		""",
        (slime, user)
      )
      .map(_ => ())

val namespacedKeyCodec = text.imap { str => NamespacedKey.fromString(str) } {
  it => it.asString()
}

class CustomEntityManager(using sql: Storage.SQLManager):
  sql.applyMigration(
    Storage.Migration(
      "Initial Custom Entity Manager",
      List(
        sql"""
				CREATE TABLE CustomEntities (
					Type TEXT NOT NULL,
					InteractionEntityID UUID NOT NULL,
					DisplayEntityID UUID NOT NULL,
					UNIQUE(InteractionEntityID),
					Unique(DisplayEntityID)
				);
				""".command
      ),
      List(
        sql"""
				DROP TABLE CustomEntities;
				""".command
      )
    )
  )

  def addEntity(
                 interaction: Interaction,
                 display: ItemDisplay,
                 kind: NamespacedKey
               )(using Session[IO]): IO[Unit] =
    sql
      .commandIO(
        sql"""
		INSERT INTO CustomEntities (
			Type, InteractionEntityID, DisplayEntityID
		) VALUES (
			$namespacedKeyCodec, $uuid, $uuid
		);
		""",
        (kind, interaction.getUniqueId(), display.getUniqueId())
      )
      .map(_ => ())

  def entitiesOfKind(kind: String)(using Session[IO]): IO[List[EntityIDPair]] =
    sql.queryListIO(
      sql"""
		SELECT InteractionEntityID, DisplayEntityUUID FROM CustomEntities WHERE kind = $text
		""",
      (uuid *: uuid).to[EntityIDPair],
      kind
    )

  def entityKind(
                  of: UUID
                )(using Session[IO]): IO[Option[(NamespacedKey, UUID)]] =
    sql.queryOptionIO(
      sql"""
		SELECT Type, DisplayEntityID FROM CustomEntities WHERE InteractionEntityID = $uuid
		""",
      (namespacedKeyCodec *: uuid),
      of
    )

  inline def entityKind(of: Interaction)(using
                                         Session[IO]
  ): IO[Option[(NamespacedKey, UUID)]] =
    entityKind(of.getUniqueId())

inline def castOption[T] =
  (ent: Entity) =>
    if ent.isInstanceOf[T] then Some(ent.asInstanceOf[T]) else None

class SlimeBehaviours()(using
                        cem: CustomEntityManager,
                        p: Plugin,
                        sql: Storage.SQLManager
) extends Listener:
  val randomizer = scala.util.Random()

  def doSlimeLooks(): Unit =
    Bukkit.getWorlds().forEach { world =>
      world.getLoadedChunks().foreach { chunk =>
        p.getServer()
          .getRegionScheduler()
          .run(
            p,
            world,
            chunk.getX(),
            chunk.getZ(),
            _ => {
              chunk
                .getEntities()
                .flatMap(castOption[Interaction])
                .foreach {
                  interaction =>
                    sql.useBlocking(cem.entityKind(interaction)) match
                      case Some((kind, disp)) if kind == Slimes.entityKind =>
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
                            Bukkit.getEntity(disp).setRotation(loc.getYaw(), 0)
                        }
                      case _ =>
                }
            }
          )
      }
    }

class SlimeEgg(using
               cem: CustomEntityManager,
               ssm: SigilSlimeManager,
               hnm: CivBeaconManager,
               sql: Storage.SQLManager
              ) extends CustomItem,
  Listeners.ItemUsedOnBlock:
  def group = Sigil.group

  def template = Slimes.slimeEggStack

  override def onItemUsedOnBlock(event: PlayerInteractEvent): Unit =
    val beacon =
      sql.useBlocking(hnm.getBeaconFor(event.getPlayer().getUniqueId())) match
        case None =>
          import BallCore.UI.ChatElements.*
          event
            .getPlayer()
            .sendServerMessage(
              txt"You must have a Civilization Heart placed to spawn a Sigil Slime!"
                .color(Colors.red)
            )
          event.setCancelled(true)
          return
        case Some(value) =>
          value

    val block = event.getClickedBlock()
    val world = block.getWorld()

    val targetXZ = block.getLocation().clone().tap(_.add(0.5, 1, 0.5))

    val targetModelLocation =
      targetXZ.clone().tap(_.add(0, Slimes.heightBlocks, 0))
    val itemDisplay = world
      .spawnEntity(targetModelLocation, EntityType.ITEM_DISPLAY)
      .asInstanceOf[ItemDisplay]
    val scale = Slimes.slimeScale
    itemDisplay.setTransformation(
      Transformation(
        Vector3f(),
        AxisAngle4f(),
        Vector3f(scale.toFloat),
        AxisAngle4f()
      )
    )
    itemDisplay.setItemStack(Slimes.sigilSlime)

    val interaction = world
      .spawnEntity(targetXZ, EntityType.INTERACTION)
      .asInstanceOf[Interaction]
    interaction.setInteractionHeight(Slimes.heightBlocks.toFloat)
    interaction.setInteractionWidth(Slimes.heightBlocks.toFloat)
    interaction.setResponsive(true)

    sql.useBlocking(cem.addEntity(interaction, itemDisplay, Slimes.entityKind))
    sql.useBlocking(ssm.addSlime(interaction.getUniqueId(), beacon))
