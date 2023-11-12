package BallCore.CraftingStations

import BallCore.CustomItems.{CustomItemStack, ItemGroup}
import BallCore.TextComponents.txt
import BallCore.UI.Prompts
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey, Tag}

import scala.List

object RedstoneMaker {
    val recipes: List[Recipe] = List(
        Recipe(
            "Make Observer",
            List(
                (MaterialChoice(Material.REDSTONE), 8),
                (MaterialChoice(Material.COBBLESTONE), 24),
                (MaterialChoice(Material.QUARTZ), 4),
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
                (MaterialChoice(Material.STONE), 12),
                (MaterialChoice(Material.REDSTONE_TORCH), 8),
                (MaterialChoice(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.REPEATER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Redstone Comparator",
            List(
                (MaterialChoice(Material.REDSTONE_TORCH), 12),
                (MaterialChoice(Material.QUARTZ), 4),
                (MaterialChoice(Material.STONE), 12),
            ),
            List(ItemStack(Material.COMPARATOR, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Piston",
            List(
                (MaterialChoice(Tag.PLANKS), 12),
                (MaterialChoice(Material.STONE), 16),
                (MaterialChoice(Material.IRON_INGOT), 4),
                (MaterialChoice(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.PISTON, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Sticky Piston",
            List(
                (MaterialChoice(Material.PISTON), 32),
                (MaterialChoice(Material.SLIME_BALL), 8),
            ),
            List(ItemStack(Material.STICKY_PISTON, 32)),
            10,
            1,
        ),
        Recipe(
            "Make Dispenser",
            List(
                (MaterialChoice(Material.STICK), 12),
                (MaterialChoice(Material.STRING), 12),
                (MaterialChoice(Material.COBBLESTONE), 28),
                (MaterialChoice(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.DISPENSER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Dropper",
            List(
                (MaterialChoice(Material.COBBLESTONE), 28),
                (MaterialChoice(Material.REDSTONE), 4),
            ),
            List(ItemStack(Material.DROPPER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Hopper",
            List(
                (MaterialChoice(Material.IRON_INGOT), 20),
                (MaterialChoice(Tag.PLANKS), 32),
            ),
            List(ItemStack(Material.HOPPER, 64)),
            20,
            1,
        ),
        Recipe(
            "Make Redstone Lamp",
            List(
                (MaterialChoice(Material.GLOWSTONE), 4),
                (MaterialChoice(Material.REDSTONE), 16),
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
