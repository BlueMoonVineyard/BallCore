// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.event.block.BlockBreakEvent
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.Location
import org.bukkit.Particle
import scala.util.chaining._

class Listener(using rm: ReinforcementManager, holos: HologramManager) extends org.bukkit.event.Listener:
    def reinforcementFromItem(is: ItemStack): Option[ReinforcementTypes] =
        if is == null then return None
        is.getType() match
            case Material.STONE => Some(ReinforcementTypes.Stone)
            case Material.DEEPSLATE => Some(ReinforcementTypes.Deepslate)
            case Material.IRON_INGOT => Some(ReinforcementTypes.IronLike)
            case Material.COPPER_INGOT => Some(ReinforcementTypes.CopperLike)
            case _ => None

    def centered(at: Location): Location =
        at.clone().tap(_.add(0.5, 0.5, 0.5))

    def playCreationEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone |
                 ReinforcementTypes.Deepslate => (Particle.PORTAL, 200, 0.0, 0.8)
            case ReinforcementTypes.CopperLike => (Particle.NAUTILUS, 100, 0.1, 0.6)
            case ReinforcementTypes.IronLike => (Particle.ENCHANTMENT_TABLE, 50, 0.0, 0.5)
        at.getWorld().spawnParticle(pType, centered(at), pCount, pOffset, pOffset, pOffset, pSpeed, null)

    def playDamageEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone |
                 ReinforcementTypes.Deepslate => (Particle.SMOKE_NORMAL, 40, 0.0, 0.06)
            case ReinforcementTypes.CopperLike => (Particle.BUBBLE_POP, 100, 0.1, 0.2)
            case ReinforcementTypes.IronLike => (Particle.END_ROD, 15, 0.0, 0.045)
        at.getWorld().spawnParticle(pType, centered(at), pCount, pOffset, pOffset, pOffset, pSpeed, null)

    def playBreakEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone |
                 ReinforcementTypes.Deepslate => (Particle.SMOKE_LARGE, 80, 0.0, 0.04)
            case ReinforcementTypes.CopperLike => (Particle.BUBBLE_POP, 500, 0.1, 0.5)
            case ReinforcementTypes.IronLike => (Particle.END_ROD, 200, 0.0, 0.13)
        at.getWorld().spawnParticle(pType, centered(at), pCount, pOffset, pOffset, pOffset, pSpeed, null)

    //
    //// Stuff that interacts with the RSM; i.e. that mutates block states
    //

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing(_) => ()
            case Unreinforcing() => ()
            case ReinforceAsYouGo(gid, item) =>
                val p = event.getPlayer()
                val i = p.getInventory()
                val loc = event.getBlockPlaced()
                val slot = i.getStorageContents.find { x =>
                    item.getType() == x.getType() && item.getItemMeta().getDisplayName() == x.getItemMeta().getDisplayName()
                }
                slot match
                    case None =>
                        event.getPlayer().sendMessage(s"no items")
                        // TODO: you ran out of items :/
                        event.setCancelled(true)
                    case Some(value) =>
                        val reinforcement = reinforcementFromItem(value)
                        if reinforcement.isEmpty then
                            event.getPlayer().sendMessage(s"${value}")
                            event.setCancelled(true)
                            return
                        rm.reinforce(p.getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID(), reinforcement.get) match
                            case Left(err) =>
                                event.getPlayer().sendMessage(explain(err))
                            case Right(_) =>
                                value.setAmount(value.getAmount()-1)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBreak(event: BlockBreakEvent): Unit =
        val block = event.getBlock()
        rm.break(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID()) match
            case Left(err) =>
                holos.clear(block)
                err match
                    case JustBroken(bs) =>
                        playBreakEffect(block.getLocation(), bs.kind)
                    case _ =>
                        // event.getPlayer().sendMessage(explain(err))
            case Right(value) =>
                playDamageEffect(block.getLocation(), value.kind)
                holos.display(block, event.getPlayer(), List(s"${value.health} / ${value.kind.hp}"))
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onInteract(event: PlayerInteractEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing(gid) =>
                if event.getAction() != Action.RIGHT_CLICK_BLOCK then
                    return
                val reinforcement = reinforcementFromItem(event.getItem())
                if reinforcement.isEmpty then
                    // TODO: reject item use
                    return
                val loc = event.getClickedBlock()
                val wid = loc.getWorld().getUID()
                rm.reinforce(event.getPlayer().getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), wid, reinforcement.get) match
                    case Left(err) =>
                        event.getPlayer().sendMessage(explain(err))
                    case Right(ok) =>
                        playCreationEffect(loc.getLocation(), reinforcement.get)
                        event.getItem().setAmount(event.getItem().getAmount()-1)
                        event.setCancelled(true)
            case Unreinforcing() =>
                if event.getAction() != Action.RIGHT_CLICK_BLOCK then
                    return

                val loc = event.getClickedBlock()
                val wid = loc.getWorld().getUID()
                rm.unreinforce(event.getPlayer().getUniqueId(), loc.getX(), loc.getY(), loc.getZ(), wid) match
                    case Left(err) =>
                        event.getPlayer().sendMessage(explain(err))
                    case Right(ok) =>
                        event.setCancelled(true)
                        // TODO: return the materials to the player
            case ReinforceAsYouGo(_, _) => ()
        