package CivCubed.CraftingStations

import RecipeIngredient.*
import org.bukkit.Material
import CivCubed.CustomItems.CustomItemStack
import org.bukkit.plugin.Plugin
import CivCubed.TextComponents._
import org.bukkit.NamespacedKey
import CivCubed.UI.Prompts
import CivCubed.CustomItems.ItemGroup
import CivCubed.Shops.SellOrder
import CivCubed.Shops.ShopChest
import CivCubed.Storage.SQLManager
import CivCubed.CustomItems.ItemRegistry

object Economist:
    val recipes = List(
        Recipe(
            trans"recipes.create-shop-chest",
            NamespacedKey("civcubed", "create_shop_chest"),
            List(
                (Vanilla(Material.CHEST), 1)
            ),
            List((ShopChest.template, 1)),
            5,
            1,
        ),
        Recipe(
            trans"recipes.create-sell-order",
            NamespacedKey("civcubed", "create_sell_order"),
            List(
                (Vanilla(Material.PAPER), 1)
            ),
            List((SellOrder.template, 1)),
            1,
            1,
        ),
    )

    val template = CustomItemStack.make(
        NamespacedKey("civcubed", "economist"),
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
