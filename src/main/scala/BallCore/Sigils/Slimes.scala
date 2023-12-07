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
import org.joml.Quaternionf
import skunk.Transaction
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.Location

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
    sql.applyMigration(
        Storage.Migration(
            "Sigil Slime HP",
            List(
                sql"""
                ALTER TABLE SigilSlimes ADD COLUMN Health INTEGER;
                """.command,
                sql"""
                UPDATE SigilSlimes SET Health = 10;
                """.command,
                sql"""
                ALTER TABLE SigilSlimes ALTER COLUMN Health SET NOT NULL;
                """.command,
            ),
            List(
                sql"""
                ALTER TABLE SigilSlimes DROP COLUMN Health;
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
            BeaconID, InteractionEntityID, Health
        ) VALUES (
            $uuid, $uuid, 10
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
    ): IO[Option[Location]] =
        sql.queryOptionIO(
            sql"""
        SELECT X, Y, Z, World FROM CustomEntities WHERE InteractionEntityID = (
            SELECT InteractionEntityID FROM SigilSlimes WHERE BanishedUserID = $uuid AND BeaconID = $uuid
        );
        """,
            (float8 *: float8 *: float8 *: uuid),
            (user, from),
        ).map(_.map { case (x, y, z, world) =>
            Location(Bukkit.getWorld(world), x, y, z)
        })

    def banish(user: UserID, slime: UUID)(using
        Session[IO],
        Transaction[IO],
    ): IO[Boolean] =
        for {
            alreadyBanished <- sql.queryUniqueIO(
                sql"""
            SELECT EXISTS(
                SELECT 1 FROM SigilSlimes WHERE BanishedUserID = $uuid AND BeaconID =
                    (SELECT BeaconID FROM SigilSlimes WHERE InteractionEntityID = $uuid)
            );
            """,
                bool,
                (user, slime),
            )
            result <-
                if alreadyBanished then IO.pure(false)
                else
                    sql.commandIO(
                        sql"""
                UPDATE SigilSlimes
                SET
                    BanishedUserID = $uuid
                WHERE
                    InteractionEntityID = $uuid;
                """,
                        (user, slime),
                    ).map(_ => true)
        } yield result

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

    def slap(
        slime: UUID
    )(using Session[IO], Transaction[IO]): IO[(Int, Boolean)] =
        sql.queryUniqueIO(
            sql"""
        UPDATE SigilSlimes SET Health = Health - 1 WHERE InteractionEntityID = $uuid RETURNING Health;
        """,
            int4,
            slime,
        ).flatMap { health =>
            if health <= 0 then
                sql.commandIO(
                    sql"""
                DELETE FROM SigilSlimes WHERE InteractionEntityID = $uuid;
                """,
                    slime,
                ).map(_ => (0, true))
            else IO.pure((health, false))
        }

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
                    X DOUBLE PRECISION NOT NULL,
                    Y DOUBLE PRECISION NOT NULL,
                    Z DOUBLE PRECISION NOT NULL,
                    World UUID NOT NULL,
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
    sql.applyMigration(
        Storage.Migration(
            "Store locations",
            List(
                sql"""
                ALTER TABLE CustomEntities ADD COLUMN X DOUBLE PRECISION;
                """.command,
                sql"""
                ALTER TABLE CustomEntities ADD COLUMN Y DOUBLE PRECISION;
                """.command,
                sql"""
                ALTER TABLE CustomEntities ADD COLUMN Z DOUBLE PRECISION;
                """.command,
                sql"""
                ALTER TABLE CustomEntities ADD COLUMN World UUID;
                """.command,
                sql"""
                UPDATE CustomEntities SET X = 0, Y = 0, Z = 0, World = '858c3b67-006f-4477-9bb8-2fe2db5b9907';
                """.command,
                sql"""
                ALTER TABLE CustomEntities ALTER COLUMN X SET NOT NULL;
                """.command,
                sql"""
                ALTER TABLE CustomEntities ALTER COLUMN Y SET NOT NULL;
                """.command,
                sql"""
                ALTER TABLE CustomEntities ALTER COLUMN Z SET NOT NULL;
                """.command,
                sql"""
                ALTER TABLE CustomEntities ALTER COLUMN World SET NOT NULL;
                """.command,
            ),
            List(
                sql"""
                ALTER TABLE CustomEntities DROP COLUMN X;
                """.command,
                sql"""
                ALTER TABLE CustomEntities DROP COLUMN Y;
                """.command,
                sql"""
                ALTER TABLE CustomEntities DROP COLUMN Z;
                """.command,
                sql"""
                ALTER TABLE CustomEntities DROP COLUMN World;
                """.command,
            ),
        )
    )

    private val cache = TrieMap[UUID, (NamespacedKey, UUID)]()

    def addEntity(
        interaction: Interaction,
        display: ItemDisplay,
        kind: NamespacedKey,
    )(using Session[IO]): IO[Unit] =
        val x = interaction.getX()
        val y = interaction.getY()
        val z = interaction.getZ()
        val world = interaction.getWorld().getUID
        sql.commandIO(
            sql"""
        INSERT INTO CustomEntities (
            Type, InteractionEntityID, DisplayEntityID, X, Y, Z, World
        ) VALUES (
            $namespacedKeyCodec, $uuid, $uuid, $float8, $float8, $float8, $uuid
        );
        """,
            (
                kind,
                interaction.getUniqueId(),
                display.getUniqueId(),
                x,
                y,
                z,
                world,
            ),
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
                            val display = Bukkit
                                .getEntity(entity.display)
                                .asInstanceOf[ItemDisplay]
                            val existingTransformation =
                                display.getTransformation()
                            display.setTransformation(
                                Transformation(
                                    existingTransformation.getTranslation(),
                                    AxisAngle4f(
                                        (360f - loc.getYaw()).toRadians,
                                        0,
                                        1,
                                        0,
                                    ).get(Quaternionf()),
                                    existingTransformation.getScale(),
                                    existingTransformation.getRightRotation(),
                                )
                            )
                    }
            }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onSlap(event: EntityDamageByEntityEvent): Unit =
        if !event.getEntity().isInstanceOf[Interaction] then return
        if !event.getDamager().isInstanceOf[Player] then return

        val intr = event.getEntity().asInstanceOf[Interaction]

        val display = sql.useBlocking(
            sql.withS(cem.entityKind(intr))
        ) match
            case Some((ent, disp)) if ent == Slimes.entityKind =>
                Bukkit.getEntity(disp).asInstanceOf[ItemDisplay]
            case _ =>
                return

        sql.useFireAndForget(
            for {
                result <- sql.withS(sql.withTX(ssm.slap(intr.getUniqueId)))
                _ <- result match
                    case (_, true) =>
                        IO {
                            display.remove()
                            intr.remove()
                        }.evalOn(EntityExecutionContext(intr))
                    case (hp, false) =>
                        IO {
                            val size = hp.toFloat / 10f
                            val scale = Slimes.slimeScale.toFloat * size
                            val translation =
                                Slimes.heightBlocks.toFloat * (1f - size)
                            display.setInterpolationDelay(-1)
                            display.setInterpolationDuration(5)
                            val existing = display.getTransformation()
                            display.setTransformation(
                                Transformation(
                                    Vector3f(0f, -translation, 0f),
                                    existing.getLeftRotation(),
                                    Vector3f(scale),
                                    existing.getRightRotation(),
                                )
                            )
                        }.evalOn(EntityExecutionContext(intr))
            } yield ()
        )

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
            ok <- sql.withS(sql.withTX(ssm.banish(plr, intr.getUniqueId)))
            _ <-
                if ok then
                    IO {
                        display.setInterpolationDelay(-1)
                        display.setInterpolationDuration(5)
                        val existing = display.getTransformation()
                        val oldScale = existing.getScale()
                        val oldTranslation = existing.getTranslation()
                        display.setTransformation(
                            Transformation(
                                Vector3f(0f, -Slimes.heightBlocks.toFloat, 0f),
                                existing.getLeftRotation(),
                                Vector3f(0f, 0f, 0f),
                                existing.getRightRotation(),
                            )
                        )
                        val _ = display
                            .getScheduler()
                            .runDelayed(
                                p,
                                _ => {
                                    display.setItemStack(Slimes.boundSigilSlime)
                                    display.setInterpolationDelay(-1)
                                    display.setInterpolationDuration(5)
                                    val existing = display.getTransformation()
                                    display.setTransformation(
                                        Transformation(
                                            oldTranslation,
                                            existing.getLeftRotation(),
                                            oldScale,
                                            existing.getRightRotation(),
                                        )
                                    )
                                },
                                () => (),
                                6,
                            )
                    }.evalOn(EntityExecutionContext(display))
                else
                    IO {
                        event
                            .getPlayer()
                            .sendServerMessage(
                                txt"That person is already banished from here!"
                            )
                    }
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
