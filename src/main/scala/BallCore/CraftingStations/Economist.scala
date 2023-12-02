package BallCore.CraftingStations

import RecipeIngredient.*
import org.bukkit.Material
import BallCore.CustomItems.CustomItemStack
import org.bukkit.plugin.Plugin
import BallCore.TextComponents._
import org.bukkit.NamespacedKey
import BallCore.UI.Prompts
import BallCore.CustomItems.ItemGroup
import BallCore.Shops.SellOrder
import BallCore.Shops.ShopChest

object Economist:
    val recipes = List(
        Recipe(
            "Create Shop Chest",
            List(
                (Vanilla(Material.CHEST), 1),
            ),
            List(ShopChest.template),
            5,
            1,
        ),
        Recipe(
            "Create Sell Order",
            List(
                (Vanilla(Material.PAPER), 1),
            ),
            List(SellOrder.template),
            1, 1,
        ),
    )

    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "economist"),
        Material.CARTOGRAPHY_TABLE,
        txt"Economist",
        txt"Creates shop chests and orders",
    )

class Economist()(using act: CraftingActor, p: Plugin, prompts: Prompts)
    extends CraftingStation(Slimer.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = Economist.template