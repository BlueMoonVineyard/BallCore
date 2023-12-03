package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.TextComponents.txt
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}
import RecipeIngredient.*
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry

object HerbivoreKitchen {
    val pairs
        : List[(List[(Vanilla, Int)], List[(Material, Int)], String)] =
        List(
            (
                List((Vanilla(Material.WHEAT), 32)),
                List(((Material.BREAD), 32)),
                "Bread",
            ),
            (
                List((Vanilla(Material.POTATO), 32)),
                List((Material.BAKED_POTATO, 32)),
                "Baked Potato",
            ),
            (
                List(
                    (Vanilla(Material.WHEAT), 32),
                    (Vanilla(Material.COCOA_BEANS), 16),
                ),
                List((Material.COOKIE, 32)),
                "Cookie",
            ),
            (
                List(
                    (Vanilla(Material.PUMPKIN), 32),
                    (Vanilla(Material.SUGAR), 32),
                    (Vanilla(Material.EGG), 32),
                ),
                List((Material.PUMPKIN_PIE, 64)),
                "Pumpkin Pie",
            ),
            (
                List(
                    (Vanilla(Material.GOLD_NUGGET), 128),
                    (Vanilla(Material.CARROT), 16),
                ),
                List((Material.GOLDEN_CARROT, 32)),
                "Golden Carrot",
            ),
            (
                List(
                    (Vanilla(Material.MILK_BUCKET), 3),
                    (Vanilla(Material.SUGAR), 2),
                    (Vanilla(Material.EGG), 1),
                    (Vanilla(Material.WHEAT), 3),
                ),
                List((Material.CAKE, 1), (Material.BUCKET, 3)),
                "Cake",
            ),
        )

    val recipes: List[Recipe] = pairs.flatMap { it =>
        val (recipe, output, name) = it

        List(
            Recipe(
                s"Make $name (low players, low efficiency)",
                recipe,
                output.map { (material, count) =>
                    ItemStack(material, count * 2)
                },
                10,
                1,
            ),
            Recipe(
                s"Make $name (medium players, medium efficiency)",
                recipe,
                output.map { (material, count) =>
                    ItemStack(material, count * 3)
                },
                10,
                2,
            ),
            Recipe(
                s"Make $name (high players, high efficiency)",
                recipe,
                output.map { (material, count) =>
                    ItemStack(material, count * 5)
                },
                20,
                4,
            ),
        )
    }

    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "herbivore_kitchen"),
        Material.SMOKER,
        txt"Herbivore Kitchen",
        txt"Makes non-meat foods with greater efficiency",
    )
}

class HerbivoreKitchen()(using CraftingActor, Plugin, Prompts, SQLManager, ItemRegistry)
    extends CraftingStation(HerbivoreKitchen.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = HerbivoreKitchen.template
