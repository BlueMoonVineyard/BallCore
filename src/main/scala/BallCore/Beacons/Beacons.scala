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
    ): Unit =
        registry.register(new HeartBlock())
