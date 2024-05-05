package CivCubed.CraftingStations

import RecipeIngredient.*
import org.bukkit.Material
import CivCubed.CustomItems.CustomItemStack
import org.bukkit.plugin.Plugin
import CivCubed.TextComponents._
import org.bukkit.NamespacedKey
import CivCubed.UI.Prompts
import CivCubed.CustomItems.ItemGroup
import CivCubed.Storage.SQLManager
import CivCubed.CustomItems.ItemRegistry
import org.bukkit.inventory.ItemStack

object IceBox:
    val recipes = List(
        Recipe(
            trans"recipes.pack-ice",
            NamespacedKey("civcubed", "pack_ice"),
            List(
                (Vanilla(Material.ICE), 512)
            ),
            List((ItemStack(Material.PACKED_ICE), 256)),
            5,
            1,
        ),
        Recipe(
            trans"recipes.pack-blue-ice",
            NamespacedKey("civcubed", "pack_blue_ice"),
            List(
                (Vanilla(Material.PACKED_ICE), 512)
            ),
            List((ItemStack(Material.BLUE_ICE), 128)),
            6,
            2,
        ),
        Recipe(
            trans"recipes.chill-lava",
            NamespacedKey("civcubed", "chill_lava"),
            List(
                (Vanilla(Material.BLUE_ICE), 64),
                (Vanilla(Material.LAVA_BUCKET), 1), // todo: overstuffed bundles
            ),
            List(
                (ItemStack(Material.OBSIDIAN), 128),
                (ItemStack(Material.BUCKET), 1),
            ),
            6,
            2,
        ),
    )

    val template = CustomItemStack.make(
        NamespacedKey("civcubed", "icebox"),
        Material.CAULDRON,
        trans"items.ice-box",
        trans"items.ice-box.lore",
    )

class IceBox()(using CraftingActor, Plugin, Prompts, SQLManager, ItemRegistry)
    extends CraftingStation(IceBox.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = IceBox.template
