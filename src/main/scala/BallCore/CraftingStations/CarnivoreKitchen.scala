package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.TextComponents.*
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}
import RecipeIngredient.*
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry

object CarnivoreKitchen {
    private val meats: List[(Material, Material)] = List(
        (Material.BEEF, Material.COOKED_BEEF),
        (Material.PORKCHOP, Material.COOKED_PORKCHOP),
        (Material.CHICKEN, Material.COOKED_CHICKEN),
        (Material.COD, Material.COOKED_COD),
        (Material.SALMON, Material.COOKED_SALMON),
        (Material.MUTTON, Material.COOKED_MUTTON),
        (Material.RABBIT, Material.COOKED_RABBIT),
    )

    private val mushrooms: List[Material] = List(
        Material.RED_MUSHROOM,
        Material.BROWN_MUSHROOM,
    )

    val recipes: List[Recipe] = mushrooms.flatMap { it =>
        List(
            Recipe(
                txt"Make ${Material.RABBIT_STEW.asComponent}",
                NamespacedKey("ballcore", "make_rabbit_stew"),
                List(
                    (Vanilla(Material.COOKED_RABBIT), 4),
                    (Vanilla(Material.CARROT), 4),
                    (Vanilla(Material.BAKED_POTATO), 4),
                    (Vanilla(it), 4),
                    (Vanilla(Material.BOWL), 4),
                ),
                List(
                    (ItemStack(Material.RABBIT_STEW), 16)
                ),
                20,
                1,
            )
        )
    } ::: meats.flatMap { it =>
        val (input, output) = it

        val key = input.getKey().toString().replace(':', '_')

        List(
            Recipe(
                txt"Cook ${input.asComponent} (low players & efficiency)",
                NamespacedKey("ballcore", s"cook_${key}_low"),
                List((Vanilla(input), 32)),
                List((ItemStack(output), 64)), // 32 * 2
                10,
                1,
            ),
            Recipe(
                txt"Cook ${input.asComponent} (medium players & efficiency)",
                NamespacedKey("ballcore", s"cook_${key}_medium"),
                List((Vanilla(input), 32)),
                List((ItemStack(output), 96)), // 32 * 3
                10,
                2,
            ),
            Recipe(
                txt"Cook ${input.asComponent} (high players & efficiency)",
                NamespacedKey("ballcore", s"cook_${key}_high"),
                List((Vanilla(input), 32)),
                List((ItemStack(output), 160)), // 32 * 5
                20,
                4,
            ),
        )
    }

    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "carnivore_kitchen"),
        Material.SMOKER,
        txt"Carnivore Kitchen",
        txt"Cooks meat with greater efficiency",
    )
}

class CarnivoreKitchen()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(CarnivoreKitchen.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = CarnivoreKitchen.template
