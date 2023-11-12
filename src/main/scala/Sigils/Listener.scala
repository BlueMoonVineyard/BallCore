// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Sigils

import BallCore.Beacons.CivBeaconManager
import BallCore.CustomItems.ItemRegistry
import BallCore.DataStructures.Actor
import BallCore.Folia.{EntityExecutionContext, FireAndForget}
import BallCore.Storage.SQLManager
import org.bukkit.Bukkit
import org.bukkit.entity.{Entity, Player, Projectile, Wolf}
import org.bukkit.event.entity.{EntityDamageByEntityEvent, EntityDeathEvent}
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.plugin.Plugin

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters.*

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
        damages = damages.updated(
          against,
          inner.updatedWith(from) { x => Some(x.getOrElse(0.0) + damage) }
        )
      case DamageMessage.getEligiblePlayers(damaging, promise) =>
        val who = damages
          .get(damaging)
          .map(_.toList.sortBy(_._2))
          .map(_.map(_._1))
          .getOrElse(List())
        promise.success(who)
      case DamageMessage.tick =>
        damages = damages
          .map { (damaged, records) =>
            damaged -> records
              .map { (damager, damage) =>
                damager -> (damage - 1.0)
              }
              .filterNot(_._2 <= 0)
          }
          .filterNot(_._2.values.sum <= 0)

  override protected def handleInit(): Unit =
    val _ = p
      .getServer()
      .getAsyncScheduler()
      .runAtFixedRate(p, _ => send(DamageMessage.tick), 0, 1, TimeUnit.SECONDS)

  override protected def handleShutdown(): Unit = ()

class DamageListener(using da: DamageActor) extends Listener:
  def getPlayerOrigin(e: Entity): Player =
    if e.isInstanceOf[Player] then e.asInstanceOf[Player]
    else if e.isInstanceOf[Projectile] then
      val proj = e.asInstanceOf[Projectile]
      if proj.getShooter().isInstanceOf[Player] then
        proj.getShooter().asInstanceOf[Player]
      else null
    else if e.isInstanceOf[Wolf] then
      val wolf = e.asInstanceOf[Wolf]
      if wolf.getOwner().isInstanceOf[Player] then
        wolf.getOwner().asInstanceOf[Player]
      else null
    else null

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  def onEntityDamageByEntity(e: EntityDamageByEntityEvent): Unit =
    if !e.getEntity().isInstanceOf[Player] then return

    val player = e.getEntity().asInstanceOf[Player]
    val damager = getPlayerOrigin(e.getDamager())
    if damager == null then return
      da.damage(damager, player, e.getDamage())

class SigilListener(using
                    ssm: SigilSlimeManager,
                    hnm: CivBeaconManager,
                    da: DamageActor,
                    ir: ItemRegistry,
                    p: Plugin,
                    sql: SQLManager
                   ) extends Listener:
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  def onPlayerMove(event: PlayerMoveEvent): Unit =
    val banished =
      sql.useBlocking(hnm.beaconContaining(event.getTo())).exists { gid =>
        sql.useBlocking(ssm.isBanished(event.getPlayer().getUniqueId(), gid))
      }
    if banished then event.setCancelled(true)

  private def doSigilBinding(killed: Player, on: List[UUID]): Unit =
    on match
      case head :: next =>
        val attacker = Bukkit.getPlayer(head)
        if attacker == null then return doSigilBinding(killed, next)

        given ec: ExecutionContext = EntityExecutionContext(attacker)

        FireAndForget {
          val searched =
            attacker
              .getInventory()
              .all(Sigil.itemStack.getType())
              .asScala
              .find((_, is) => {
                val item = ir.lookup(is)
                if !item.isDefined || !item.get.isInstanceOf[Sigil] then false
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
                  val _ = attacker
                    .getWorld()
                    .dropItemNaturally(attacker.getLocation(), newStack)
              ()
        }
      case Nil => ()

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  def onEntityDeath(event: EntityDeathEvent): Unit =
    if !event.getEntity().isInstanceOf[Player] then return

    val killed = event.getEntity().asInstanceOf[Player]

    given ec: ExecutionContext = EntityExecutionContext(killed)

    da.getEligiblePlayers(killed)
      .foreach(players => doSigilBinding(killed, players))
