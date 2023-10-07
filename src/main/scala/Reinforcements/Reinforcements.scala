// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Groups.GroupManager
import BallCore.UI.Prompts
import BallCore.CustomItems.ItemRegistry
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Server
import BallCore.Beacons.CivBeaconManager
import BallCore.Storage.SQLManager

object Reinforcements:
    def registerBlockListener()(using server: Server, registry: ItemRegistry, plugin: JavaPlugin, cbm: CivBeaconManager, gm: GroupManager, holos: HologramManager, prompts: Prompts, sql: SQLManager): Unit =
        server.getPluginManager().registerEvents(Listener(), plugin)

    def registerEntityListener()(using server: Server, registry: ItemRegistry, plugin: JavaPlugin, erm: EntityReinforcementManager, gm: GroupManager, holos: HologramManager, sql: SQLManager): Unit =
        server.getPluginManager().registerEvents(EntityListener(), plugin)

    def register()(using registry: ItemRegistry, server: Server, plugin: JavaPlugin, cbm: CivBeaconManager, erm: EntityReinforcementManager, gm: GroupManager, holos: HologramManager, prompts: Prompts, sql: SQLManager): Unit =
        registerBlockListener()
        registerEntityListener()

