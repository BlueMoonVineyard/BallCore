package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.TextComponents.txt
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey, Tag}
import RecipeIngredient.*
import scala.List
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry

object RedstoneMaker {
    val recipes: List[Recipe] = List(
        Recipe(
            txt"Make Observer",
            NamespacedKey("ballcore", "make_observer"),
            List(
                (Vanilla(Material.REDSTONE), 8),
                (Vanilla(Material.COBBLESTONE), 24),
                (Vanilla(Material.QUARTZ), 4),
            ),
            List(
                (ItemStack(Material.OBSERVER), 64)
            ),
            20,
            1,
        ),
        Recipe(
            txt"Make Redstone Repeater",
            NamespacedKey("ballcore", "make_redstone_repeater"),
            List(
                (Vanilla(Material.STONE), 12),
                (Vanilla(Material.REDSTONE_TORCH), 8),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List((ItemStack(Material.REPEATER), 64)),
            20,
            1,
        ),
        Recipe(
            txt"Make Redstone Comparator",
            NamespacedKey("ballcore", "make_redstone_comparator"),
            List(
                (Vanilla(Material.REDSTONE_TORCH), 12),
                (Vanilla(Material.QUARTZ), 4),
                (Vanilla(Material.STONE), 12),
            ),
            List((ItemStack(Material.COMPARATOR), 64)),
            20,
            1,
        ),
        Recipe(
            txt"Make Piston",
            NamespacedKey("ballcore", "make_piston"),
            List(
                (TagList(Tag.PLANKS), 12),
                (Vanilla(Material.STONE), 16),
                (Vanilla(Material.IRON_INGOT), 4),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List((ItemStack(Material.PISTON), 64)),
            20,
            1,
        ),
        Recipe(
            txt"Make Sticky Piston",
            NamespacedKey("ballcore", "make_sticky_piston"),
            List(
                (Vanilla(Material.PISTON), 32),
                (Vanilla(Material.SLIME_BALL), 8),
            ),
            List((ItemStack(Material.STICKY_PISTON), 32)),
            10,
            1,
        ),
        Recipe(
            txt"Make Dispenser",
            NamespacedKey("ballcore", "make_dispenser"),
            List(
                (Vanilla(Material.STICK), 12),
                (Vanilla(Material.STRING), 12),
                (Vanilla(Material.COBBLESTONE), 28),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List((ItemStack(Material.DISPENSER), 64)),
            20,
            1,
        ),
        Recipe(
            txt"Make Dropper",
            NamespacedKey("ballcore", "make_dropper"),
            List(
                (Vanilla(Material.COBBLESTONE), 28),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List((ItemStack(Material.DROPPER), 64)),
            20,
            1,
        ),
        Recipe(
            txt"Make Hopper",
            NamespacedKey("ballcore", "make_hopper"),
            List(
                (Vanilla(Material.IRON_INGOT), 20),
                (TagList(Tag.PLANKS), 32),
            ),
            List((ItemStack(Material.HOPPER), 64)),
            20,
            1,
        ),
        Recipe(
            txt"Make Redstone Lamp",
            NamespacedKey("ballcore", "make_redstone_lamp"),
            List(
                (Vanilla(Material.GLOWSTONE), 4),
                (Vanilla(Material.REDSTONE), 16),
            ),
            List((ItemStack(Material.REDSTONE_LAMP), 64)),
            20,
            1,
        ),
        Recipe(
            txt"Make Elevators",
            NamespacedKey("ballcore", "make_elevators"),
            List(
                (Vanilla(Material.IRON_INGOT), 8),
                (Vanilla(Material.GOLD_INGOT), 8),
                (Vanilla(Material.COPPER_INGOT), 16),
                (Vanilla(Material.REDSTONE), 16),
            ),
            List((ItemStack(Material.LODESTONE), 2)),
            5,
            1,
        ),
        Recipe(
            txt"Make Redstone Torches",
            NamespacedKey("ballcore", "make_redstone_torches"),
            List(
                (Vanilla(Material.STICK), 16),
                (Vanilla(Material.REDSTONE), 16),
            ),
            List((ItemStack(Material.REDSTONE_TORCH), 64)),
            5,
            1,
        ),
    )

    val template: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "redstone_maker"),
        Material.PISTON,
        txt"Redstone Maker",
        txt"Crafts redstone related blocks at bulk rates",
    )
}

class RedstoneMaker()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(RedstoneMaker.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = RedstoneMaker.template
