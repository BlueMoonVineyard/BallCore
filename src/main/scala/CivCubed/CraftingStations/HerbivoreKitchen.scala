package CivCubed.CraftingStations

import CivCubed.CustomItems.{CustomItemStack, ItemGroup}
import CivCubed.TextComponents.*
import CivCubed.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}
import RecipeIngredient.*
import CivCubed.Storage.SQLManager
import CivCubed.CustomItems.ItemRegistry

object HerbivoreKitchen {
    val pairs: List[(List[(Vanilla, Int)], List[(Material, Int)])] =
        List(
            (
                List((Vanilla(Material.WHEAT), 32)),
                List(((Material.BREAD), 32)),
            ),
            (
                List((Vanilla(Material.POTATO), 32)),
                List((Material.BAKED_POTATO, 32)),
            ),
            (
                List(
                    (Vanilla(Material.WHEAT), 32),
                    (Vanilla(Material.COCOA_BEANS), 16),
                ),
                List((Material.COOKIE, 32)),
            ),
            (
                List(
                    (Vanilla(Material.PUMPKIN), 32),
                    (Vanilla(Material.SUGAR), 32),
                    (Vanilla(Material.EGG), 32),
                ),
                List((Material.PUMPKIN_PIE, 64)),
            ),
            (
                List(
                    (Vanilla(Material.GOLD_NUGGET), 128),
                    (Vanilla(Material.CARROT), 16),
                ),
                List((Material.GOLDEN_CARROT, 32)),
            ),
            (
                List(
                    (Vanilla(Material.MILK_BUCKET), 3),
                    (Vanilla(Material.SUGAR), 2),
                    (Vanilla(Material.EGG), 1),
                    (Vanilla(Material.WHEAT), 3),
                ),
                List((Material.CAKE, 1), (Material.BUCKET, 3)),
            ),
        )

    val recipes: List[Recipe] = pairs.flatMap { (recipe, output) =>
        val first = output.head._1
        val key = first.getKey().toString().replace(':', '_')

        List(
            Recipe(
                trans"recipes.make-herbivore-recipe.low-efficiency".args(first.asComponent),
                NamespacedKey("civcubed", s"make_$key"),
                recipe,
                output.map { (material, count) =>
                    (ItemStack(material), count * 2)
                },
                10,
                1,
            ),
            Recipe(
                trans"recipes.make-herbivore-recipe.medium-efficiency".args(first.asComponent),
                NamespacedKey("civcubed", s"make_$key"),
                recipe,
                output.map { (material, count) =>
                    (ItemStack(material), count * 3)
                },
                10,
                2,
            ),
            Recipe(
                trans"recipes.make-herbivore-recipe.high-efficiency".args(first.asComponent),
                NamespacedKey("civcubed", s"make_$key"),
                recipe,
                output.map { (material, count) =>
                    (ItemStack(material), count * 5)
                },
                20,
                4,
            ),
        )
    }

    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("civcubed", "herbivore_kitchen"),
        Material.SMOKER,
        trans"items.herbivore-kitchen",
        trans"items.herbivore-kitchen.lore",
    )
}

class HerbivoreKitchen()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(HerbivoreKitchen.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = HerbivoreKitchen.template
