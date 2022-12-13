package BallCore.Hearts

import BallCore.Storage

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import org.bukkit.plugin.java.JavaPlugin

object Hearts:
    val group = ItemGroup(NamespacedKey("ballcore", "hearts"), ItemStack(Material.WHITE_CONCRETE))

    def registerItems()(using addon: SlimefunAddon, kvs: Storage.KeyVal, jp: JavaPlugin) =
        (new HeartBlock()).register(addon)
