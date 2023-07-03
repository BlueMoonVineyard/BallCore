// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

import BallCore.Storage

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.ItemRegistry
import org.bukkit.Server
import BallCore.CustomItems.BlockManager
import BallCore.PolygonEditor.PolygonEditor
import BallCore.Groups.GroupManager

object Beacons:
    val group = ItemGroup(NamespacedKey("ballcore", "hearts"), ItemStack(Material.WHITE_CONCRETE))

    def registerItems()(using registry: ItemRegistry, pe: PolygonEditor, gm: GroupManager, bm: BlockManager, hn: CivBeaconManager, server: Server, plugin: JavaPlugin) =
        registry.register(new HeartBlock())
