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

object HeartBlock:
    val itemStack = CustomItemStack.make(NamespacedKey("ballcore", "civilization_heart"), Material.WHITE_CONCRETE, "&rCivilization Heart", "&rIt beats with the power of a budding civilization...")
    // val tickHandler = RainbowTickHandler(Material.WHITE_CONCRETE, Material.PINK_CONCRETE, Material.RED_CONCRETE, Material.PINK_CONCRETE)

class HeartBlockListener(using registry: ItemRegistry) extends Listener:
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent) =
        val itemStack = event.getPlayer().getInventory().getItemInMainHand()
        registry.lookup(itemStack) match
            case Some(h: HeartBlock) =>
                if h.playerHasHeart(event.getPlayer()) then
                    event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart is already placed...")
                    event.setCancelled(true)
            case _ =>
                ()

class HeartBlock()(using hn: HeartNetworkManager, server: Server, plugin: JavaPlugin) extends CustomItem:
    // override def preRegister(): Unit =
    //     jp.getServer().getPluginManager().registerEvents(HeartBlockListener, jp)
    //     addItemHandler(HeartBlock.tickHandler, onPlace, onBreak)

    def group = Hearts.group
    def template = HeartBlock.itemStack

    def playerHasHeart(p: Player): Boolean =
        hn.hasHeart(p.getUniqueId())

    // private def onPlace = new BlockPlaceHandler(false):
    //     override def onPlayerPlace(e: BlockPlaceEvent): Unit =
    //         BlockStorage.addBlockInfo(e.getBlock(), "owner", e.getPlayer().getUniqueId().toString())
    //         hn.placeHeart(e.getBlock().getLocation(), e.getPlayer().getUniqueId()) match
    //             case Some((_, x)) if x == 1 =>
    //                 e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart has started a new core!")
    //                 e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}It will strengthen your power in this land...")
    //                 e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You can join forces with other players by having them place their hearts on the core.")
    //             case Some((_, x)) =>
    //                 e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've joined your heart to a core with ${x-1} other players!")
    //             case None =>
    //                 ()

    // private def onBreak = new BlockBreakHandler(false, false):
    //     override def onPlayerBreak(e: BlockBreakEvent, item: ItemStack, drops: ju.List[ItemStack]): Unit =
    //         val l = e.getBlock().getLocation()
    //         val owner = UUID.fromString(BlockStorage.getLocationInfo(l, "owner"))
    //         hn.removeHeart(e.getBlock().getLocation(), owner) match
    //             case Some(_) =>
    //                 e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've disconnected from the core...")
    //             case None =>
    //                 e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've deleted the core...")
