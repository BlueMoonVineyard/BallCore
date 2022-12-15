package BallCore.Hearts

import BallCore.Storage
import io.github.thebusybiscuit.slimefun4.core.handlers.RainbowTickHandler
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import org.bukkit.Material
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler
import org.bukkit.event.block.BlockPlaceEvent
import me.mrCookieSlime.Slimefun.api.BlockStorage
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import java.{util => ju}
import java.util.UUID
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.ChatColor

object HeartBlock:
    val itemStack = SlimefunItemStack("CIVILIZATION_HEART", Material.WHITE_CONCRETE, "&rCivilization Heart", "&rIt beats with the power of a budding civilization...")
    val tickHandler = RainbowTickHandler(Material.WHITE_CONCRETE, Material.PINK_CONCRETE, Material.RED_CONCRETE, Material.PINK_CONCRETE)

object HeartBlockListener extends Listener:
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent) =
        val itemStack = event.getPlayer().getInventory().getItemInMainHand()
        SlimefunItem.getByItem(itemStack) match
            case h: HeartBlock =>
                if h.playerHasHeart(event.getPlayer()) then
                    event.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart is already placed...")
                    event.setCancelled(true)

class HeartBlock()(using kvs: Storage.KeyVal, sf: SlimefunAddon, jp: JavaPlugin)
    extends SlimefunItem(Hearts.group, HeartBlock.itemStack, RecipeType.NULL, null):

    override def preRegister(): Unit =
        jp.getServer().getPluginManager().registerEvents(HeartBlockListener, jp)
        addItemHandler(HeartBlock.tickHandler, onPlace, onBreak)

    def playerHasHeart(p: Player): Boolean =
        HeartNetwork.hasHeart(p.getUniqueId())

    private def onPlace = new BlockPlaceHandler(false):
        override def onPlayerPlace(e: BlockPlaceEvent): Unit =
            BlockStorage.addBlockInfo(e.getBlock(), "owner", e.getPlayer().getUniqueId().toString())
            HeartNetwork.placeHeart(e.getBlock().getLocation(), e.getPlayer().getUniqueId()) match
                case Some((_, x)) if x.players.length == 1 =>
                    e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}Your heart has started a new core!")
                    e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}It will strengthen your power in this land...")
                    e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You can join forces with other players by having them place their hearts on the core.")
                case Some((_, x)) =>
                    e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've joined your heart to a core with ${x.players.length-1} other players!")
                case None =>
                    ()

    private def onBreak = new BlockBreakHandler(false, false):
        override def onPlayerBreak(e: BlockBreakEvent, item: ItemStack, drops: ju.List[ItemStack]): Unit =
            val l = e.getBlock().getLocation()
            val owner = UUID.fromString(BlockStorage.getLocationInfo(l, "owner"))
            HeartNetwork.removeHeart(e.getBlock().getLocation(), owner) match
                case Some(_) =>
                    e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've disconnected from the core...")
                case None =>
                    e.getPlayer().sendMessage(s"${ChatColor.LIGHT_PURPLE}You've deleted the core...")
