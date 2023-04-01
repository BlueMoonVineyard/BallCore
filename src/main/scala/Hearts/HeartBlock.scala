// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Hearts

import BallCore.Storage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import java.{util => ju}
import java.util.UUID
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.ChatColor
import BallCore.CustomItems.CustomItemStack
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.ItemRegistry
import org.bukkit.Server
import BallCore.CustomItems.Listeners
import BallCore.CustomItems.BlockManager

object HeartBlock:
    val itemStack = CustomItemStack.make(NamespacedKey("ballcore", "civilization_heart"), Material.WHITE_CONCRETE, "&rCivilization Heart", "&rIt beats with the power of a budding civilization...")
    // val tickHandler = RainbowTickHandler(Material.WHITE_CONCRETE, Material.PINK_CONCRETE, Material.RED_CONCRETE, Material.PINK_CONCRETE)

class HeartBlock()(using hn: HeartNetworkManager, server: Server, plugin: JavaPlugin, bm: BlockManager)
    extends CustomItem, Listeners.BlockPlaced, Listeners.BlockRemoved:

    def group = Hearts.group
    def template = HeartBlock.itemStack

    def playerHasHeart(p: Player): Boolean =
        hn.hasHeart(p.getUniqueId())

    def onBlockPlace(event: BlockPlaceEvent): Unit =
        if playerHasHeart(event.getPlayer()) then
            event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart is already placed...")
            event.setCancelled(true)
            return
        bm.store(event.getBlock(), "owner", event.getPlayer().getUniqueId())
        hn.placeHeart(event.getBlock().getLocation(), event.getPlayer().getUniqueId()) match
            case Some((_, x)) if x == 1 =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart has started a new core!")
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}It will strengthen your power in this land...")
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You can join forces with other players by having them place their hearts on the core.")
            case Some((_, x)) =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've joined your heart to a core with ${x-1} other players!")
            case None =>
                ()

    def onBlockRemoved(event: BlockBreakEvent): Unit =
        val l = event.getBlock().getLocation()
        val owner = bm.retrieve[UUID](event.getBlock(), "owner").get
        hn.removeHeart(event.getBlock().getLocation(), owner) match
            case Some(_) =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've disconnected from the core...")
            case None =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've deleted the core...")
