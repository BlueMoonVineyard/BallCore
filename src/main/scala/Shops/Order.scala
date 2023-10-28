package BallCore.Shops

import BallCore.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.CustomItems.ItemRegistry

object Order:
    val group = ItemGroup(NamespacedKey("ballcore", "orders"), ItemStack(Material.PAPER))
    val buyOrderCMD = 4
    val sellOrderCMD = 5

    def register()(using registry: ItemRegistry): Unit =
        registry.register(BuyOrder())
        registry.register(SellOrder())
        registry.register(ShopChest())
