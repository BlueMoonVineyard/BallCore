// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action

class Listener(using rm: ReinforcementManager) extends org.bukkit.event.Listener:
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing() => ()
            case Unreinforcing() => ()
            case ReinforceAsYouGo() => ()

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onInteract(event: PlayerInteractEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing() => ()
                // if event.getAction() != Action.RIGHT_CLICK_BLOCK then
                //     return
                // event.getItem().setAmount(event.getItem().getAmount()-1)
            case Unreinforcing() => ()
            case ReinforceAsYouGo() => ()
        