// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Beacons.CivBeaconManager
import BallCore.CustomItems.ItemRegistry
import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager
import BallCore.UI.Prompts
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import BallCore.Fingerprints.FingerprintManager
import BallCore.WebHooks.WebHookManager
import BallCore.PrimeTime.PrimeTimeManager
import BallCore.CustomItems.BlockManager

object Reinforcements:
    private def registerBlockListener()(using
        server: Server,
        plugin: JavaPlugin,
        cbm: CivBeaconManager,
        gm: GroupManager,
        sql: SQLManager,
        busts: BustThroughTracker,
        fingerprints: FingerprintManager,
        webhooks: WebHookManager,
        primeTime: PrimeTimeManager,
        blockManager: BlockManager,
        ir: ItemRegistry,
    ): Unit =
        server.getPluginManager.registerEvents(Listener(), plugin)

    private def registerEntityListener()(using
        server: Server,
        plugin: JavaPlugin,
        erm: EntityReinforcementManager,
        gm: GroupManager,
        sql: SQLManager,
    ): Unit =
        server.getPluginManager.registerEvents(EntityListener(), plugin)

    def register()(using
        registry: ItemRegistry,
        server: Server,
        plugin: JavaPlugin,
        cbm: CivBeaconManager,
        erm: EntityReinforcementManager,
        gm: GroupManager,
        holos: HologramManager,
        prompts: Prompts,
        sql: SQLManager,
        busts: BustThroughTracker,
        fingerprints: FingerprintManager,
        webhooks: WebHookManager,
        primeTime: PrimeTimeManager,
        blockManager: BlockManager,
    ): Unit =
        registerBlockListener()
        registerEntityListener()
