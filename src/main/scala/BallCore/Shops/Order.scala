package BallCore.Shops

import BallCore.CustomItems.{ItemGroup, ItemRegistry}
import org.bukkit.inventory.{ItemStack, StonecuttingRecipe}
import org.bukkit.{Material, NamespacedKey}

object Order:
    val group: ItemGroup =
        ItemGroup(
            NamespacedKey("ballcore", "orders"),
            ItemStack(Material.PAPER),
        )
    val buyOrderCMD = 4
    val sellOrderCMD = 5

    def register()(using registry: ItemRegistry): Unit =
        registry.register(BuyOrder())
        registry.register(SellOrder())
        val soRecipe = StonecuttingRecipe(
            NamespacedKey("ballcore", "sell_order_recipe"),
            SellOrder.template,
            Material.PAPER,
        )
        registry.addRecipe(soRecipe)
        registry.register(ShopChest())
        val scRecipe = StonecuttingRecipe(
            NamespacedKey("ballcore", "shop_chest_recipe"),
            ShopChest.template,
            Material.CHEST,
        )
        registry.addRecipe(scRecipe)
