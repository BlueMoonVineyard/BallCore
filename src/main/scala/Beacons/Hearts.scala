// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.BlockManager
import BallCore.PolygonEditor.PolygonEditor
import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager

object Beacons:
    val group = ItemGroup(NamespacedKey("ballcore", "hearts"), ItemStack(Material.WHITE_CONCRETE))

    def registerItems()(using registry: ItemRegistry, pe: PolygonEditor, gm: GroupManager, bm: BlockManager, hn: CivBeaconManager, sql: SQLManager) =
        registry.register(new HeartBlock())
