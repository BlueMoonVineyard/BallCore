package BallCore.CraftingStations

import RecipeIngredient.*
import org.bukkit.Material
import BallCore.Sigils.Slimes
import BallCore.Sigils.Sigil
import scala.util.chaining._
import BallCore.CustomItems.CustomItemStack
import org.bukkit.plugin.Plugin
import BallCore.TextComponents._
import org.bukkit.NamespacedKey
import BallCore.UI.Prompts
import BallCore.CustomItems.ItemGroup

object Slimer:
    val recipes = List(
        Recipe(
            "Create Sigil Slime Egg",
            List(
                (Vanilla(Material.EGG), 1),
                (Vanilla(Material.SLIME_BALL), 1),
            ),
            List(Slimes.slimeEggStack),
            10,
            2,
        ),
        Recipe(
            "Create Sigils",
            List(
                (Vanilla(Material.HONEYCOMB), 2),
                (Vanilla(Material.RED_DYE), 2),
                (Vanilla(Material.ENDER_PEARL), 1),
            ),
            List(Sigil.itemStack.clone().tap(_.setAmount(4))),
            10, 2,
        ),
    )

    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "slimer"),
        Material.CRAFTING_TABLE,
        txt"Slimer",
        txt"Creates sigils and sigil slimes",
    )

class Slimer()(using act: CraftingActor, p: Plugin, prompts: Prompts)
    extends CraftingStation(Slimer.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = Slimer.template
