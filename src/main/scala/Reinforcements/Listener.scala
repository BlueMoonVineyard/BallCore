// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.EventPriority

class Listener() extends org.bukkit.event.Listener:
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing() => ()
            case Unreinforcing() => ()
