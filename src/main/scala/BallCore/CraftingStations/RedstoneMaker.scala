package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.TextComponents.txt
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey, Tag}
import RecipeIngredient.*
import scala.List

object RedstoneMaker {
    val recipes: List[Recipe] = List(
        Recipe(
            "Make Observer",
            List(
                (Vanilla(Material.REDSTONE), 8),
                (Vanilla(Material.COBBLESTONE), 24),
                (Vanilla(Material.QUARTZ), 4),
            ),
            List(
                ItemStack(Material.OBSERVER, 64)
            ),
            20,
            1,
        ),
        Recipe(
            "Make Redstone Repeater",
            List(
                (Vanilla(Material.STONE), 12),
                (Vanilla(Material.REDSTONE_TORCH), 8),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.REPEATER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Redstone Comparator",
            List(
                (Vanilla(Material.REDSTONE_TORCH), 12),
                (Vanilla(Material.QUARTZ), 4),
                (Vanilla(Material.STONE), 12),
            ),
            List(ItemStack(Material.COMPARATOR, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Piston",
            List(
                (TagList(Tag.PLANKS), 12),
                (Vanilla(Material.STONE), 16),
                (Vanilla(Material.IRON_INGOT), 4),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.PISTON, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Sticky Piston",
            List(
                (Vanilla(Material.PISTON), 32),
                (Vanilla(Material.SLIME_BALL), 8),
            ),
            List(ItemStack(Material.STICKY_PISTON, 32)),
            10,
            1,
        ),
        Recipe(
            "Make Dispenser",
            List(
                (Vanilla(Material.STICK), 12),
                (Vanilla(Material.STRING), 12),
                (Vanilla(Material.COBBLESTONE), 28),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.DISPENSER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Dropper",
            List(
                (Vanilla(Material.COBBLESTONE), 28),
                (Vanilla(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.DROPPER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Hopper",
            List(
                (Vanilla(Material.IRON_INGOT), 20),
                (TagList(Tag.PLANKS), 32),
            ),
            List(ItemStack(Material.HOPPER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Redstone Lamp",
            List(
                (Vanilla(Material.GLOWSTONE), 4),
                (Vanilla(Material.REDSTONE), 16),
            ),
            List(ItemStack(Material.REDSTONE_LAMP, 64)),
            20,
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

class RedstoneMaker()(using act: CraftingActor, p: Plugin, prompts: Prompts)
    extends CraftingStation(RedstoneMaker.recipes):
    def group: ItemGroup = CraftingStations.group

    def template: CustomItemStack = RedstoneMaker.template
