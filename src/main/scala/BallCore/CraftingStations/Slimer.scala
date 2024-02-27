package BallCore.CraftingStations

import RecipeIngredient.*
import org.bukkit.Material
import BallCore.Sigils.Slimes
import BallCore.Sigils.Sigil
import BallCore.CustomItems.CustomItemStack
import org.bukkit.plugin.Plugin
import BallCore.TextComponents._
import org.bukkit.NamespacedKey
import BallCore.UI.Prompts
import BallCore.CustomItems.ItemGroup
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry

object Slimer:
    val recipes = List(
        Recipe(
            trans"recipes.create-sigil-slime-egg",
            NamespacedKey("ballcore", "create_sigil_slime_egg"),
            List(
                (Vanilla(Material.EGG), 1),
                (Vanilla(Material.SLIME_BALL), 1),
            ),
            List((Slimes.slimeEggStack, 1)),
            10,
            2,
        ),
        Recipe(
            trans"recipes.create-sigil",
            NamespacedKey("ballcore", "create_sigils"),
            List(
                (Vanilla(Material.HONEYCOMB), 2),
                (Vanilla(Material.RED_DYE), 2),
                (Vanilla(Material.ENDER_PEARL), 1),
            ),
            List((Sigil.itemStack, 4)),
            10,
            2,
        ),
    )

    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "slimer"),
        Material.CRAFTING_TABLE,
        trans"items.slimer",
        trans"items.slimer.lore",
    )

class Slimer()(using CraftingActor, Plugin, Prompts, SQLManager, ItemRegistry)
    extends CraftingStation(Slimer.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = Slimer.template
