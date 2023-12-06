// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Sigils

import org.bukkit.Material
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import scala.util.chaining._
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.Listeners
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import org.joml.Vector3f
import org.joml.AxisAngle4f
import org.bukkit.entity.Interaction
import org.bukkit.inventory.ItemStack
import BallCore.Storage
import java.util.UUID
import scala.jdk.CollectionConverters._
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import org.bukkit.plugin.Plugin
import org.bukkit.entity.Entity
import org.bukkit.event.Listener
import BallCore.Beacons.CivBeaconManager
import BallCore.Beacons.BeaconID
import BallCore.Groups.UserID
import BallCore.Folia.FireAndForget
import BallCore.UI.Elements._
import skunk.implicits._
import skunk.codec.all._
import cats.effect.IO
import skunk.Session
import scala.collection.concurrent.TrieMap
import BallCore.CustomItems.ItemRegistry
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority

object Slimes:
    val sigilSlime = ItemStack(Material.STICK)
    sigilSlime.setItemMeta(
        sigilSlime.getItemMeta().tap(_.setCustomModelData(6))
    )
    val boundSigilSlime = ItemStack(Material.STICK)
    boundSigilSlime.setItemMeta(
        boundSigilSlime.getItemMeta().tap(_.setCustomModelData(8))
    )
    val slimeEggStack = CustomItemStack.make(
        NamespacedKey("ballcore", "sigil_slime_egg"),
        Material.PAPER,
        txt"Sigil Slime Egg",
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
    ccm: CustomEntityManager,
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
            ),
        )
    )
    val _ = cbm
    val _ = ccm

    def addSlime(entity: UUID, beacon: BeaconID)(using Session[IO]): IO[Unit] =
        sql.commandIO(
            sql"""
        INSERT INTO SigilSlimes (
            BeaconID, InteractionEntityID
        ) VALUES (
            $uuid, $uuid
        )
        """,
            (beacon, entity),
        ).map(_ => ())

    def banishedUsers(from: BeaconID)(using Session[IO]): IO[List[UserID]] =
        sql.queryListIO(
            sql"""
        SELECT BanishedUserID FROM SigilSlimes WHERE BeaconID = $uuid AND BanishedUserID IS NOT NULL;
        """,
            uuid,
            from,
        )

    def isBanished(user: UserID, from: BeaconID)(using
        Session[IO]
    ): IO[Boolean] =
        sql.queryUniqueIO(
            sql"""
        SELECT EXISTS (
            SELECT 1 FROM SigilSlimes WHERE BanishedUserID = $uuid AND BeaconID = $uuid
        );
        """,
            bool,
            (user, from),
        )

    def banish(user: UserID, slime: UUID)(using Session[IO]): IO[Unit] =
        sql.commandIO(
            sql"""
        UPDATE SigilSlimes
        SET
            BanishedUserID = $uuid
        WHERE
            InteractionEntityID = $uuid;
        """,
            (user, slime),
        ).map(_ => ())

    def unbanish(user: UserID, slime: UUID)(using Session[IO]): IO[Unit] =
        sql.commandIO(
            sql"""
        UPDATE SigilSlimes
        SET
            BanishedUserID = NULL
        WHERE
            InteractionEntityID = $uuid AND
            BanishedUserID = $uuid;
        """,
            (slime, user),
        ).map(_ => ())

val namespacedKeyCodec = text.imap { str => NamespacedKey.fromString(str) } {
    it => it.asString()
}

class CustomEntityManager(using sql: Storage.SQLManager, p: Plugin):
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
            ),
        )
    )

    private val cache = TrieMap[UUID, (NamespacedKey, UUID)]()

    def addEntity(
        interaction: Interaction,
        display: ItemDisplay,
        kind: NamespacedKey,
    )(using Session[IO]): IO[Unit] =
        sql.commandIO(
            sql"""
        INSERT INTO CustomEntities (
            Type, InteractionEntityID, DisplayEntityID
        ) VALUES (
            $namespacedKeyCodec, $uuid, $uuid
        );
        """,
            (kind, interaction.getUniqueId(), display.getUniqueId()),
        ).flatMap { _ =>
            IO {
                cache(interaction.getUniqueId()) = (kind, display.getUniqueId())
            }
        }

    def deleteEntity(
        interaction: Interaction
    )(using Session[IO]): IO[Unit] =
        sql.queryUniqueIO(
            sql"""
            DELETE FROM CustomEntities WHERE InteractionEntityID = $uuid RETURNING DisplayEntityID;
            """,
            uuid,
            interaction.getUniqueId(),
        ).flatMap { displayID =>
            IO {
                interaction.remove()
                Bukkit.getEntity(displayID).remove()
            }.evalOn(EntityExecutionContext(interaction))
        }

    def entitiesOfKind(kind: NamespacedKey)(using
        Session[IO]
    ): IO[List[EntityIDPair]] =
        sql.queryListIO(
            sql"""
        SELECT InteractionEntityID, DisplayEntityID FROM CustomEntities WHERE Type = $namespacedKeyCodec
        """,
            (uuid *: uuid).to[EntityIDPair],
            kind,
        )

    def entityKind(
        of: UUID
    )(using Session[IO]): IO[Option[(NamespacedKey, UUID)]] =
        IO {
            cache.get(of)
        }.flatMap {
            case Some(res) => IO.pure(Some(res))
            case _ =>
                sql.queryOptionIO(
                    sql"""
                SELECT Type, DisplayEntityID FROM CustomEntities WHERE InteractionEntityID = $uuid
                """,
                    (namespacedKeyCodec *: uuid),
                    of,
                ).flatTap {
                    case Some(res) =>
                        IO {
                            cache(of) = res
                        }
                    case res => IO.pure(res)
                }
        }

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
    sql: Storage.SQLManager,
    ir: ItemRegistry,
    ssm: SigilSlimeManager,
) extends Listener:
    val randomizer = scala.util.Random()

    def doSlimeLooks(): Unit =
        sql.useBlocking(sql.withS(cem.entitiesOfKind(Slimes.entityKind)))
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onRightClick(event: PlayerInteractEntityEvent): Unit =
        if !event.getRightClicked().isInstanceOf[Interaction] then return
        val is = event.getPlayer().getInventory().getItemInMainHand()

        val plr = ir.lookup(is) match
            case Some(x: Sigil) =>
                x.boundPlayerIn(is) match
                    case None =>
                        return
                    case Some(player) =>
                        player
            case _ =>
                return
        val _ = plr

        val intr = event.getRightClicked().asInstanceOf[Interaction]

        val display = sql.useBlocking(
            sql.withS(cem.entityKind(intr))
        ) match
            case Some((ent, disp)) if ent == Slimes.entityKind =>
                Bukkit.getEntity(disp).asInstanceOf[ItemDisplay]
            case _ =>
                return

        sql.useFireAndForget(for {
            _ <- sql.withS(ssm.banish(plr, intr.getUniqueId))
            _ <- IO {
                display.setInterpolationDelay(-1)
                display.setInterpolationDuration(5)
                val existing = display.getTransformation()
                display.setTransformation(Transformation(
                    Vector3f(0f, -Slimes.heightBlocks.toFloat, 0f),
                    existing.getLeftRotation(),
                    Vector3f(0f, 0f, 0f),
                    existing.getRightRotation(),
                ))
                val _ = display.getScheduler().runDelayed(p, _ => {
                    display.setItemStack(Slimes.boundSigilSlime)
                    display.setInterpolationDelay(-1)
                    display.setInterpolationDuration(5)
                    val existing = display.getTransformation()
                    display.setTransformation(Transformation(
                        Vector3f(0f, 0f, 0f),
                        existing.getLeftRotation(),
                        Vector3f(Slimes.slimeScale.toFloat),
                        existing.getRightRotation(),
                    ))
                }, () => (), 6)
            }.evalOn(EntityExecutionContext(display))
        } yield ())

class SlimeEgg(using
    cem: CustomEntityManager,
    ssm: SigilSlimeManager,
    hnm: CivBeaconManager,
    sql: Storage.SQLManager,
) extends CustomItem,
      Listeners.ItemUsedOnBlock:
    def group = Sigil.group
    def template = Slimes.slimeEggStack

    override def onItemUsedOnBlock(event: PlayerInteractEvent): Unit =
        val beacon =
            sql.useBlocking(
                sql.withS(hnm.getBeaconFor(event.getPlayer().getUniqueId()))
            ) match
                case None =>
                    import BallCore.UI.ChatElements._
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
        event.getItem().setAmount(event.getItem().getAmount() - 1)

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
                AxisAngle4f(),
            )
        )
        itemDisplay.setItemStack(Slimes.sigilSlime)

        val interaction = world
            .spawnEntity(targetXZ, EntityType.INTERACTION)
            .asInstanceOf[Interaction]
        interaction.setInteractionHeight(Slimes.heightBlocks.toFloat)
        interaction.setInteractionWidth(Slimes.heightBlocks.toFloat)
        interaction.setResponsive(true)

        sql.useBlocking(
            sql.withS(
                cem.addEntity(interaction, itemDisplay, Slimes.entityKind)
            )
        )
        sql.useBlocking(
            sql.withS(ssm.addSlime(interaction.getUniqueId(), beacon))
        )
