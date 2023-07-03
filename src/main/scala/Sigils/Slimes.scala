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
import scalikejdbc.DBSession
import scalikejdbc._
import java.util.UUID
import scala.collection.JavaConverters._
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import scala.concurrent.Future
import org.bukkit.plugin.Plugin
import org.bukkit.entity.Entity
import org.bukkit.event.Listener
import BallCore.Beacons.CivBeaconManager
import BallCore.Beacons.BeaconID
import BallCore.Groups.UserID

object Slimes:
	val sigilSlime = ItemStack(Material.STICK)
	sigilSlime.setItemMeta(sigilSlime.getItemMeta().tap(_.setCustomModelData(6)))
	val slimeEggStack = CustomItemStack.make(NamespacedKey("ballcore", "sigil_slime_egg"), Material.PAPER, "&rSigil Slime Egg")
	slimeEggStack.setItemMeta(slimeEggStack.getItemMeta().tap(_.setCustomModelData(3)))

	val slimeScale = 1.5
	val heightBlocks = (8.0 * slimeScale) / 16.0

	val entityKind = NamespacedKey("ballcore", "sigil_slime")

case class EntityIDPair(val interaction: UUID, val display: UUID)

class SigilSlimeManager(using sql: Storage.SQLManager):
	private implicit val session: DBSession = sql.session

	sql.applyMigration(
		Storage.Migration(
			"Initial Sigil Slime Manager",
			List(
				sql"""
				CREATE TABLE SigilSlimes (
					BanishedUserID TEXT,
					BeaconID TEXT NOT NULL,
					InteractionEntityID TEXT NOT NULL,
					UNIQUE(BanishedUserID, BeaconID),
					UNIQUE(InteractionEntityID),
					FOREIGN KEY (BeaconID) REFERENCES Beacons(ID) ON DELETE CASCADE,
					FOREIGN KEY (InteractionEntityID) REFERENCES CustomEntities(InteractionEntityID) ON DELETE CASCADE
				);
				"""
			),
			List(
				sql"""
				DROP TABLE SigilSlimes;
				"""
			)
		),
	)

	def addSlime(entity: UUID, beacon: BeaconID): Unit =
		sql"""
		INSERT INTO SigilSlimes (
			BeaconID, InteractionEntityID
		) VALUES (
			${beacon}, ${entity}
		)
		"""
		.update
		.apply()

	def banishedUsers(from: BeaconID): List[UserID] =
		sql"""
		SELECT UserID FROM SigilSlimes WHERE BeaconID = ${from} AND BanishedUserID IS NOT NULL;
		"""
		.map(rs => UUID.fromString(rs.string("BanishedUserID")))
		.list
		.apply()

	def isBanished(user: UserID, from: BeaconID): Boolean =
		sql"""
		SELECT EXISTS (
			SELECT 1 FROM SigilSlimes WHERE BanishedUserID = ${user} AND BeaconID = ${from}
		);
		"""
		.map(rs => rs.boolean(1))
		.single
		.apply()
		.getOrElse(false)

	def banish(user: UserID, slime: UUID): Unit =
		sql"""
		UPDATE SigilSlimes
		SET
			BanishedUserID = ${user}
		WHERE
			InteractionEntityID = ${slime};
		"""
		.update
		.apply()

	def unbanish(user: UserID, slime: UUID): Unit =
		sql"""
		UPDATE SigilSlimes
		SET
			BanishedUserID = NULL
		WHERE
			InteractionEntityID = ${slime} AND
			BanishedUserID = ${user};
		"""
		.update
		.apply()


class CustomEntityManager(using sql: Storage.SQLManager):
	private implicit val session: DBSession = sql.session

	sql.applyMigration(
		Storage.Migration(
			"Initial Custom Entity Manager",
			List(
				sql"""
				CREATE TABLE CustomEntities (
					Type TEXT NOT NULL,
					InteractionEntityID TEXT NOT NULL,
					DisplayEntityID TEXT NOT NULL,
					UNIQUE(InteractionEntityID),
					Unique(DisplayEntityID)
				);
				""",
			),
			List(
				sql"""
				DROP TABLE CustomEntities;
				""",
			),
		)
	)

	def addEntity(interaction: Interaction, display: ItemDisplay, kind: NamespacedKey): Unit =
		sql"""
		INSERT INTO CustomEntities (
			Type, InteractionEntityID, DisplayEntityID
		) VALUES (
			${kind.asString()}, ${interaction.getUniqueId()}, ${display.getUniqueId()}
		);
		"""
		.update
		.apply()

	def entitiesOfKind(kind: String): List[EntityIDPair] =
		sql"""
		SELECT * FROM CustomEntities WHERE kind = ${kind}
		"""
		.map(rs => EntityIDPair(rs.string("InteractionEntityID").pipe(UUID.fromString), rs.string("DisplayEntityID").pipe(UUID.fromString)))
		.list
		.apply()

	def entityKind(of: UUID): Option[(NamespacedKey, UUID)] =
		sql"""
		SELECT Type, DisplayEntityID FROM CustomEntities WHERE InteractionEntityID = ${of}
		"""
		.map(rs => (rs.string("Type").pipe(NamespacedKey.fromString), rs.string("DisplayEntityID").pipe(UUID.fromString)))
		.single
		.apply()

	inline def entityKind(of: Interaction): Option[(NamespacedKey, UUID)] =
		entityKind(of.getUniqueId())

inline def castOption[T] =
	(ent: Entity) => if ent.isInstanceOf[T] then Some(ent.asInstanceOf[T]) else None

class SlimeBehaviours()(using cem: CustomEntityManager, p: Plugin) extends Listener:
	val randomizer = scala.util.Random()

	def doSlimeLooks(): Unit =
		Bukkit.getWorlds().forEach { world =>
			world.getLoadedChunks().foreach { chunk =>
				p.getServer().getRegionScheduler().run(p, world, chunk.getX(), chunk.getZ(), _ => {
					chunk.getEntities().flatMap(castOption[Interaction]).foreach { interaction =>
						cem.entityKind(interaction) match
							case Some((kind, disp)) if kind == Slimes.entityKind =>
								given ec: ExecutionContext = EntityExecutionContext(interaction)
								Future {
									val players =
										interaction.getNearbyEntities(10, 3, 10).asScala.flatMap(castOption[Player])
									val player = players(randomizer.nextInt(players.length))
									val loc = interaction.getLocation().clone()
										.setDirection(player.getLocation().clone().subtract(interaction.getLocation()).toVector())
									interaction.setRotation(loc.getYaw(), 0)
									Bukkit.getEntity(disp).setRotation(loc.getYaw(), 0)
								}
							case _ =>
					}
				})
			}
		}

class SlimeEgg(using cem: CustomEntityManager, ssm: SigilSlimeManager, hnm: CivBeaconManager) extends CustomItem, Listeners.ItemUsedOnBlock:
	def group = Sigil.group
	def template = Slimes.slimeEggStack

	override def onItemUsed(event: PlayerInteractEvent): Unit =
		val beacon =
			hnm.getBeaconFor(event.getPlayer().getUniqueId()) match
				case None =>
					import BallCore.UI.ChatElements._
					event.getPlayer().sendMessage(txt"You must have a Civilization Heart placed to spawn a Sigil Slime!".color(Colors.red))
					event.setCancelled(true)
					return
				case Some(value) =>
					value

		val block = event.getClickedBlock()
		val world = block.getWorld()

		val targetXZ = block.getLocation().clone().tap(_.add(0.5, 1, 0.5))

		val targetModelLocation = targetXZ.clone().tap(_.add(0, Slimes.heightBlocks, 0))
		val itemDisplay = world.spawnEntity(targetModelLocation, EntityType.ITEM_DISPLAY).asInstanceOf[ItemDisplay]
		val scale = Slimes.slimeScale
		itemDisplay.setTransformation(Transformation(Vector3f(), AxisAngle4f(), Vector3f(scale.toFloat), AxisAngle4f()))
		itemDisplay.setItemStack(Slimes.sigilSlime)

		val interaction = world.spawnEntity(targetXZ, EntityType.INTERACTION).asInstanceOf[Interaction]
		interaction.setInteractionHeight(Slimes.heightBlocks.toFloat)
		interaction.setInteractionWidth(Slimes.heightBlocks.toFloat)
		interaction.setResponsive(true)

		cem.addEntity(interaction, itemDisplay, Slimes.entityKind)
		ssm.addSlime(interaction.getUniqueId(), beacon)