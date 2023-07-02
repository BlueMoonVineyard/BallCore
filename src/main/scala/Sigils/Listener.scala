package BallCore.Sigils

import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import BallCore.Hearts.HeartNetworkManager
import BallCore.DataStructures.Actor
import org.bukkit.entity.Player
import scala.concurrent.Promise
import java.util.UUID
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.entity.Entity
import org.bukkit.entity.Projectile
import org.bukkit.entity.Wolf
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.Bukkit
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._
import BallCore.CustomItems.ItemRegistry
import BallCore.Folia.EntityExecutionContext

enum DamageMessage:
	case damage(from: UUID, against: UUID, damage: Double)
	case getEligiblePlayers(damaging: UUID, promise: Promise[List[UUID]])
	case tick

class DamageActor()(using p: Plugin) extends Actor[DamageMessage]:
	var damages = Map[UUID, Map[UUID, Double]]()

	def damage(from: Player, to: Player, damage: Double): Unit =
		send(DamageMessage.damage(from.getUniqueId(), to.getUniqueId(), damage))

	def getEligiblePlayers(damaging: Player): Future[List[UUID]] =
		val prom = Promise[List[UUID]]()
		send(DamageMessage.getEligiblePlayers(damaging.getUniqueId(), prom))
		prom.future

	override def handle(m: DamageMessage): Unit =
		m match
			case DamageMessage.damage(from, against, damage) =>
				val inner = damages.getOrElse(against, Map())
				damages = damages.updated(against, inner.updatedWith(from) { x => Some(x.getOrElse(0.0) + damage) })
			case DamageMessage.getEligiblePlayers(damaging, promise) =>
				val who = damages.get(damaging)
					.map(_.toList.sortBy(_._2))
					.map(_.map(_._1))
					.getOrElse(List())
				promise.success(who)
			case DamageMessage.tick =>
				damages = damages.map { (damaged, records) =>
					damaged -> records.map { (damager, damage) =>
						damager -> (damage - 1.0)
					}.filterNot(_._2 <= 0)
				}.filterNot(_._2.values.sum <= 0)
	override protected def handleInit(): Unit =
		p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => send(DamageMessage.tick), 0, 1, TimeUnit.SECONDS)
	override protected def handleShutdown(): Unit = ()

class DamageListener(using da: DamageActor) extends Listener:
	def getPlayerOrigin(e: Entity): Player =
		if e.isInstanceOf[Player] then
			e.asInstanceOf[Player]
		else if e.isInstanceOf[Projectile] then
			val proj = e.asInstanceOf[Projectile]
			if proj.getShooter().isInstanceOf[Player] then
				proj.getShooter().asInstanceOf[Player]
			else
				null
		else if e.isInstanceOf[Wolf] then
			val wolf = e.asInstanceOf[Wolf]
			if wolf.getOwner().isInstanceOf[Player] then
				wolf.getOwner().asInstanceOf[Player]
			else
				null
		else
			null

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	def onEntityDamageByEntity(e: EntityDamageByEntityEvent): Unit =
		if !e.getEntity().isInstanceOf[Player] then
			return

		val player = e.getEntity().asInstanceOf[Player]
		val damager = getPlayerOrigin(e.getDamager())
		if damager == null then
			return
		da.damage(damager, player, e.getDamage())

class SigilListener(using ssm: SigilSlimeManager, hnm: HeartNetworkManager, da: DamageActor, ir: ItemRegistry, p: Plugin) extends Listener:
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	def onPlayerMove(event: PlayerMoveEvent): Unit =
		val banished = hnm.heartNetworksContaining(event.getTo()).exists { gid => ssm.isBanished(event.getPlayer().getUniqueId(), gid) }
		if banished then
			event.setCancelled(true)

	private def doSigilBinding(killed: Player, on: List[UUID]): Unit =
		on match
			case head :: next =>
				val attacker = Bukkit.getPlayer(head)
				if attacker == null then
					return doSigilBinding(killed, next)

				given ec: ExecutionContext = EntityExecutionContext(attacker)
				Future {
					val searched =
						attacker.getInventory().all(Sigil.itemStack.getType()).asScala
							.find((slot, is) => {
								val item = ir.lookup(is)
								if !item.isDefined || !item.get.isInstanceOf[Sigil] then
									false
								else
									val sigil = item.get.asInstanceOf[Sigil]
									sigil.isEmpty(is)
							})

					searched match
						case None => doSigilBinding(killed, next)
						case Some(value) =>
							val (slot, stack) = value
							val sigil = ir.lookup(stack).get.asInstanceOf[Sigil]
							if stack.getAmount() == 1 then
								stack.setItemMeta(sigil.itemMetaForBound(stack, killed))
							else
								stack.setAmount(stack.getAmount() - 1)
								val newStack = stack.clone()
								newStack.setAmount(1)
								newStack.setItemMeta(sigil.itemMetaForBound(newStack, killed))
								if !attacker.getInventory().addItem(newStack).isEmpty() then
									attacker.getWorld().dropItemNaturally(attacker.getLocation(), newStack)
							()
				}
			case Nil => ()

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	def onEntityDeath(event: EntityDeathEvent): Unit =
		if !event.getEntity().isInstanceOf[Player] then
			return

		val killed = event.getEntity().asInstanceOf[Player]
		given ec: ExecutionContext = EntityExecutionContext(killed)
		da.getEligiblePlayers(killed).map(players => doSigilBinding(killed, players))