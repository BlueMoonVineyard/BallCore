package BallCore.Woodcutter

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import org.bukkit.NamespacedKey
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack
import org.bukkit.Material
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.attributes.NotConfigurable
import org.bukkit.event.Listener
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import com.destroystokyo.paper.MaterialTags
import org.bukkit.inventory.StonecuttingRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.entity.Player
import java.{util => ju}
import java.util.Collections
import me.mrCookieSlime.Slimefun.api.BlockStorage
import scala.util.chaining._
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent

object WoodCutterListener extends Listener:
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onSelectStonecutterRecipe(event: PlayerStonecutterRecipeSelectEvent): Unit =
        val inv = event.getStonecutterInventory()
        val player = event.getPlayer()
        val recp = event.getStonecuttingRecipe()
        val key = recp.getKey()
        val block = inv.getLocation().getBlock()
        if recp == null then
            return
        val result = recp.getResult()
        val amt = result.getAmount()
        if (amt != 5 && amt != 6 && amt != 7) || !Woodcutter.planks.contains(result.getType()) then
            return
        val woodcutterItem = BlockStorage.check(block)
        val (t1, t2, t3) = Woodcutter.recipes.find { triplet =>
            val (t1, t2, t3) = triplet
            t1.key() == key || t2.key() == key || t3.key() == key
        }.get
        woodcutterItem match               
            case _: WoodcutterT1 if recp.key() == t1.key() => ()
            case _: WoodcutterT2 if recp.key() == t1.key() || recp.key() == t2.key() => ()
            case _: WoodcutterT3 if recp.key() == t1.key() || recp.key() == t2.key() || recp.key() == t3.key() => ()
            case _ if recp.key() == t1.key() =>
                event.setCancelled(true)
                player.closeInventory()
                player.sendMessage("You need a Woodcutter to use that recipe!")
            case _ if recp.key() == t2.key() =>
                event.setCancelled(true)
                player.closeInventory()
                player.sendMessage("You need an Improved Woodcutter to use that recipe!")
            case _ if recp.key() == t3.key() =>
                event.setCancelled(true)
                player.closeInventory()
                player.sendMessage("You need an Advanced Woodcutter to use that recipe!")
            case _ =>
                ()

object Woodcutter:
    val itemGroup = ItemGroup(NamespacedKey("ballcore", "woodcutters"), CustomItemStack(Material.STONECUTTER, "BallCore Woodcutters"))
    val t1ItemStack = SlimefunItemStack("WOODCUTTER_TIER1", Material.STONECUTTER, "&rBasic Woodcutter", "&7Chops wood more efficiently")
    val t2ItemStack = SlimefunItemStack("WOODCUTTER_TIER2", Material.STONECUTTER, "&rImproved Woodcutter", "&7Chops wood even more efficiently")
    val t3ItemStack = SlimefunItemStack("WOODCUTTER_TIER3", Material.STONECUTTER, "&rAdvanced Woodcutter", "&7Chops wood with outstanding efficiency")

    val logs = List(
        (List(Material.ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD), Material.ACACIA_PLANKS),
        (List(Material.BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD), Material.BIRCH_PLANKS),
        // (List(Material.CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CHERRY_WOOD), Material.CHERRY_PLANKS),
        (List(Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE), Material.CRIMSON_PLANKS),
        (List(Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD), Material.DARK_OAK_PLANKS),
        (List(Material.JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD), Material.JUNGLE_PLANKS),
        // (List(Material.MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD), Material.MANGROVE_PLANKS),
        (List(Material.OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD), Material.OAK_PLANKS),
        (List(Material.SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD), Material.SPRUCE_PLANKS),
        (List(Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM, Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE), Material.WARPED_PLANKS),
    )
    val planks = logs.map(_._2)
    val recipes = logs.flatMap { row =>
        val (inputs, output) = row
        inputs.map { input =>
            val pkey1 = input.getKey().getKey()
            val pkey2 = output.getKey().getKey()
            val key1 = s"${pkey1}_to_${pkey2}_t1"
            val key2 = s"${pkey1}_to_${pkey2}_t2"
            val key3 = s"${pkey1}_to_${pkey2}_t3"
            val nskey1 = NamespacedKey("ballcore", key1)
            val nskey2 = NamespacedKey("ballcore", key2)
            val nskey3 = NamespacedKey("ballcore", key3)

            val recipe1 = StonecuttingRecipe(nskey1, ItemStack(output, 5), MaterialChoice(input))
            val recipe2 = StonecuttingRecipe(nskey2, ItemStack(output, 6), MaterialChoice(input))
            val recipe3 = StonecuttingRecipe(nskey3, ItemStack(output, 7), MaterialChoice(input))

            (recipe1, recipe2, recipe3)
        }
    }

    def registerItems()(using sf: SlimefunAddon): Unit =
        sf.getJavaPlugin().getServer().getPluginManager().registerEvents(WoodCutterListener, sf.getJavaPlugin())
        WoodcutterT1().register(sf)
        WoodcutterT2().register(sf)
        WoodcutterT3().register(sf)

        val serv = sf.getJavaPlugin().getServer()

        recipes.foreach { triplet =>
            val (t1, t2, t3) = triplet
            serv.addRecipe(t1)
            serv.addRecipe(t2)
            serv.addRecipe(t3)
        }

abstract class Woodcutter(val is: SlimefunItemStack)
    extends SlimefunItem(Woodcutter.itemGroup, is, RecipeType.NULL, null), NotConfigurable

class WoodcutterT1 extends Woodcutter(Woodcutter.t1ItemStack)
class WoodcutterT2 extends Woodcutter(Woodcutter.t2ItemStack)
class WoodcutterT3 extends Woodcutter(Woodcutter.t3ItemStack)
