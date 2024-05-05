// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Beacons

import CivCubed.CustomItems.{BlockManager, ItemGroup, ItemRegistry}
import CivCubed.Groups.GroupManager
import CivCubed.PolygonEditor.PolygonEditor
import CivCubed.Storage.SQLManager
import org.bukkit.inventory.ItemStack
import org.bukkit.{Material, NamespacedKey}
import CivCubed.Sigils.BattleManager
import CivCubed.NoodleEditor.EssenceManager
import org.bukkit.plugin.Plugin
import CivCubed.Fingerprints.FingerprintManager
import CivCubed.WebHooks.WebHookManager

object Beacons:
    val group: ItemGroup = ItemGroup(
        NamespacedKey("civcubed", "hearts"),
        ItemStack(Material.WHITE_CONCRETE),
    )

    def register()(using
        registry: ItemRegistry,
        pe: PolygonEditor,
        gm: GroupManager,
        bm: BlockManager,
        hn: CivBeaconManager,
        sql: SQLManager,
        battleManager: BattleManager,
        essence: EssenceManager,
        p: Plugin,
        fingerprints: FingerprintManager,
        webhooks: WebHookManager,
    ): Unit =
        p.getServer.getPluginManager.registerEvents(Listener(), p)
        registry.register(new HeartBlock())
