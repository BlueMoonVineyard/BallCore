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

class Listener(using rm: ReinforcementManager) extends org.bukkit.event.Listener:
    def reinforcementFromItem(is: ItemStack): Option[ReinforcementTypes] =
        is.getType() match
            case Material.STONE => Some(ReinforcementTypes.Stone)
            case Material.DEEPSLATE => Some(ReinforcementTypes.Deepslate)
            case Material.IRON_INGOT => Some(ReinforcementTypes.IronLike)
            case Material.COPPER_INGOT => Some(ReinforcementTypes.CopperLike)
            case _ => None
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing(_) => ()
            case Unreinforcing() => ()
            case ReinforceAsYouGo(_) => ()

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBreak(event: BlockBreakEvent): Unit =
        val block = event.getBlock()
        rm.break(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID()) match
            case Left(value) => ()
            case Right(value) =>
                // TODO: render hologram
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
                rm.reinforce(event.getPlayer().getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), wid, reinforcement.get.hp) match
                    case Left(err) =>
                        err match
                            case ReinforcementGroupError(error) => ()
                            case AlreadyExists() => ()
                            case DoesntExist() => ()
                    case Right(ok) =>
                        // TODO: feedback
                        event.getItem().setAmount(event.getItem().getAmount()-1)
                        event.setCancelled(true)
            case Unreinforcing() => ()
            case ReinforceAsYouGo(_) => ()
        