package BallCore.CraftingStations

import BallCore.Alloys.Tier1
import BallCore.Ores.QuadrantOres.ItemStacks
import RecipeIngredient.*
import scala.util.chaining._
import BallCore.TextComponents._
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.plugin.Plugin
import BallCore.UI.Prompts
import BallCore.CustomItems.ItemGroup
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry

object Tier1Alloyer:
    val alloys = List(
        (ItemStacks.tin, ItemStacks.copper, Tier1.bronze),
        (ItemStacks.magnesium, ItemStacks.meteorite, Tier1.magnox),
        (ItemStacks.gold, ItemStacks.iron, Tier1.gildedIron),
        (ItemStacks.palladium, ItemStacks.aluminum, Tier1.pallalumin),
    )
    val recipes = alloys.map { (a, b, alloy) =>
        Recipe(
            s"Alloy ${alloy.name}",
            List(
                (Custom(a.ingot), 32),
                (Custom(b.ingot), 32),
            ),
            List(alloy.stack.clone().tap(_.setAmount(32))),
            10,
            1,
        )
    }

    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "alloyer"),
        Material.BLAST_FURNACE,
        txt"Alloyer",
        txt"Alloys ores together into more valuable ores",
    )

class Tier1Alloyer()(using CraftingActor, Plugin, Prompts, SQLManager, ItemRegistry)
    extends CraftingStation(Tier1Alloyer.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = Tier1Alloyer.template

