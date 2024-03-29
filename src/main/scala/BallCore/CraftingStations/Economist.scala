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
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry

object Economist:
    val recipes = List(
        Recipe(
            trans"recipes.create-shop-chest",
            NamespacedKey("ballcore", "create_shop_chest"),
            List(
                (Vanilla(Material.CHEST), 1)
            ),
            List((ShopChest.template, 1)),
            5,
            1,
        ),
        Recipe(
            trans"recipes.create-sell-order",
            NamespacedKey("ballcore", "create_sell_order"),
            List(
                (Vanilla(Material.PAPER), 1)
            ),
            List((SellOrder.template, 1)),
            1,
            1,
        ),
    )

    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "economist"),
        Material.CARTOGRAPHY_TABLE,
        trans"items.economist",
        trans"items.economist.lore",
    )

class Economist()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(Economist.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = Economist.template
