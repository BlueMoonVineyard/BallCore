// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

import BallCore.CustomItems.{BlockManager, ItemGroup, ItemRegistry}
import BallCore.Groups.GroupManager
import BallCore.PolygonEditor.PolygonEditor
import BallCore.Storage.SQLManager
import org.bukkit.inventory.ItemStack
import org.bukkit.{Material, NamespacedKey}
import BallCore.Sigils.BattleManager
import BallCore.NoodleEditor.EssenceManager

object Beacons:
    val group: ItemGroup = ItemGroup(
        NamespacedKey("ballcore", "hearts"),
        ItemStack(Material.WHITE_CONCRETE),
    )

    def registerItems()(using
        registry: ItemRegistry,
        pe: PolygonEditor,
        gm: GroupManager,
        bm: BlockManager,
        hn: CivBeaconManager,
        sql: SQLManager,
        battleManager: BattleManager,
        essence: EssenceManager,
    ): Unit =
        registry.register(new HeartBlock())
