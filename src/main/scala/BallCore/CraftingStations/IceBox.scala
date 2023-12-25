package BallCore.CraftingStations

import RecipeIngredient.*
import org.bukkit.Material
import BallCore.CustomItems.CustomItemStack
import org.bukkit.plugin.Plugin
import BallCore.TextComponents._
import org.bukkit.NamespacedKey
import BallCore.UI.Prompts
import BallCore.CustomItems.ItemGroup
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry
import org.bukkit.inventory.ItemStack

object IceBox:
    val recipes = List(
        Recipe(
            txt"Pack Ice",
            NamespacedKey("ballcore", "pack_ice"),
            List(
                (Vanilla(Material.ICE), 512)
            ),
            List((ItemStack(Material.PACKED_ICE), 256)),
            5,
            1,
        ),
        Recipe(
            txt"Pack Blue Ice",
            NamespacedKey("ballcore", "pack_blue_ice"),
            List(
                (Vanilla(Material.PACKED_ICE), 512)
            ),
            List((ItemStack(Material.BLUE_ICE), 128)),
            6,
            2,
        ),
        Recipe(
            txt"Chill Lava",
            NamespacedKey("ballcore", "chill_lava"),
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
        NamespacedKey("ballcore", "icebox"),
        Material.CAULDRON,
        txt"Ice Box",
        txt"For working with ice and lava",
    )

class IceBox()(using CraftingActor, Plugin, Prompts, SQLManager, ItemRegistry)
    extends CraftingStation(IceBox.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = IceBox.template
