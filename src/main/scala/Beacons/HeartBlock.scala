// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

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
import org.bukkit.event.player.PlayerInteractEvent
import BallCore.PolygonEditor.PolygonEditor
import BallCore.Groups.GroupManager
import BallCore.Groups.Permissions
import org.bukkit.inventory.EquipmentSlot

object HeartBlock:
    val itemStack = CustomItemStack.make(NamespacedKey("ballcore", "civilization_heart"), Material.WHITE_CONCRETE, "&rCivilization Heart", "&rIt beats with the power of a budding civilization...")
    // val tickHandler = RainbowTickHandler(Material.WHITE_CONCRETE, Material.PINK_CONCRETE, Material.RED_CONCRETE, Material.PINK_CONCRETE)

class HeartBlock()(using hn: CivBeaconManager, editor: PolygonEditor, gm: GroupManager, server: Server, plugin: JavaPlugin, bm: BlockManager)
    extends CustomItem, Listeners.BlockPlaced, Listeners.BlockRemoved, Listeners.BlockClicked:

    def group = Beacons.group
    def template = HeartBlock.itemStack

    def playerHasHeart(p: Player): Boolean =
        hn.hasHeart(p.getUniqueId())

    def onBlockClicked(event: PlayerInteractEvent): Unit =
        if event.getHand() != EquipmentSlot.HAND then
            return
        hn.heartAt(event.getClickedBlock().getLocation())
            .map(_._2)
            .flatMap(beacon => hn.getGroup(beacon).map(group => (beacon, group)))
            .map { (beacon, group) =>
                gm.checkE(event.getPlayer().getUniqueId(), group, Permissions.ManageClaims) match
                    case Left(err) =>
                        event.getPlayer().sendMessage(s"You cannot edit claims because ${err.explain()}")
                    case Right(_) =>
                        editor.create(event.getPlayer(), event.getClickedBlock().getWorld(), beacon)
            }

    def onBlockPlace(event: BlockPlaceEvent): Unit =
        if playerHasHeart(event.getPlayer()) then
            event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart is already placed...")
            event.setCancelled(true)
            return
        bm.store(event.getBlock(), "owner", event.getPlayer().getUniqueId())
        hn.placeHeart(event.getBlock().getLocation(), event.getPlayer().getUniqueId()) match
            case Some((_, x)) if x == 1 =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart is the first block of a new beacon!")
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}The beacon will help you adapt to the land here faster, which will get you increased resource drops over time, among other things.")
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Bind a group to this beacon, and you'll unlock the ability to claim land and allow other players to join your beacon!")
            case Some((_, x)) =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've joined your heart to a beacon with ${x-1} other players!")
            case None =>
                ()

    def onBlockRemoved(event: BlockBreakEvent): Unit =
        val l = event.getBlock().getLocation()
        val owner = bm.retrieve[UUID](event.getBlock(), "owner").get
        hn.removeHeart(event.getBlock().getLocation(), owner) match
            case Some(_) =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've disconnected from the beacon...")
            case None =>
                event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've deleted the beacon...")
