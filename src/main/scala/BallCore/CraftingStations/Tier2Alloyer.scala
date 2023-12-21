package BallCore.CraftingStations

import BallCore.Alloys.Tier1.*
import BallCore.Alloys.Tier2.*
import BallCore.Ores.QuadrantOres.ItemStacks.*
import BallCore.Ores.CardinalOres.ItemStacks.*
import RecipeIngredient.*
import BallCore.TextComponents._
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.plugin.Plugin
import BallCore.UI.Prompts
import BallCore.CustomItems.ItemGroup
import BallCore.Storage.SQLManager
import BallCore.CustomItems.ItemRegistry

object Tier2Alloyer:
    val recipes = List(
        Recipe(
            s"Alloy Ferrobyte",
            List(
                (Custom(gildedIron.stack), 32),
                (Custom(sillicon.ingot), 32),
                (Custom(hihiirogane.ingot), 64),
                (Vanilla(Material.REDSTONE), 64),
                (Vanilla(Material.OAK_LOG), 128),
            ),
            List((ferrobyte.stack, 16)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Dawn Bronze",
            List(
                (Custom(bronze.stack), 32),
                (Custom(meteorite.ingot), 64),
                (Custom(hihiirogane.ingot), 64),
                (Vanilla(Material.REDSTONE), 64),
                (Vanilla(Material.SPRUCE_LOG), 128),
            ),
            List((skyBronzeMorning.stack, 8)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Sky Bronze",
            List(
                (Custom(bronze.stack), 32),
                (Custom(meteorite.ingot), 64),
                (Custom(hihiirogane.ingot), 64),
                (Vanilla(Material.REDSTONE), 64),
                (Vanilla(Material.SPRUCE_LOG), 128),
            ),
            List((skyBronzeDay.stack, 16)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Dusk Bronze",
            List(
                (Custom(bronze.stack), 32),
                (Custom(meteorite.ingot), 64),
                (Custom(hihiirogane.ingot), 64),
                (Vanilla(Material.REDSTONE), 64),
                (Vanilla(Material.SPRUCE_LOG), 128),
            ),
            List((skyBronzeEvening.stack, 16)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Star Bronze",
            List(
                (Custom(bronze.stack), 32),
                (Custom(meteorite.ingot), 64),
                (Custom(hihiirogane.ingot), 64),
                (Vanilla(Material.REDSTONE), 64),
                (Vanilla(Material.SPRUCE_LOG), 128),
            ),
            List((skyBronzeNight.stack, 16)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Suno",
            List(
                (Custom(silver.ingot), 64),
                (Custom(diamond), 32),
                (Vanilla(Material.COAL), 64),
                (Vanilla(Material.BIRCH_LOG), 128),
            ),
            List((suno.stack, 16)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Adamantite",
            List(
                (Custom(orichalcum.ingot), 64),
                (Custom(plutonium), 32),
                (Custom(pallalumin.stack), 32),
                (Vanilla(Material.EMERALD), 32),
                (Vanilla(Material.JUNGLE_LOG), 128),
            ),
            List((adamantite.stack, 16)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Hepatizon",
            List(
                (Custom(sapphire), 32),
                (Custom(lead.ingot), 32),
                (Custom(magnox.stack), 32),
                (Vanilla(Material.OBSIDIAN), 64),
            ),
            List((hepatizon.stack, 16)),
            10,
            2,
        ),
        Recipe(
            s"Alloy Manyullyn",
            List(
                (Custom(sulfur.ingot), 32),
                (Custom(cobalt.ingot), 32),
                (Vanilla(Material.REDSTONE), 64),
                (Vanilla(Material.ACACIA_LOG), 128),
            ),
            List((manyullyn.stack, 16)),
            10,
            2,
        ),
    )

    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "smeltery"),
        Material.BLAST_FURNACE,
        txt"Smeltery",
        txt"Hotter heats and stronger metals make for better alloys",
        txt"(Tier 2 Alloyer)",
    )

class Tier2Alloyer()(using
    CraftingActor,
    Plugin,
    Prompts,
    SQLManager,
    ItemRegistry,
) extends CraftingStation(Tier2Alloyer.recipes):
    def group: ItemGroup = CraftingStations.group
    def template: CustomItemStack = Tier2Alloyer.template
