// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon

object Reinforcements:
    def register()(using sf: SlimefunAddon, rm: ReinforcementManager, holos: HologramManager): Unit =
        val plugin = sf.getJavaPlugin()
        plugin.getServer().getPluginManager().registerEvents(Listener(), plugin)
