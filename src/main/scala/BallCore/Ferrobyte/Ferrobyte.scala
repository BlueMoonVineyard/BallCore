package BallCore.Ferrobyte

import BallCore.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.CustomItems.ItemRegistry
import org.bukkit.inventory.ShapedRecipe
import BallCore.Alloys.Tier2
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.Tag
import BallCore.CustomItems.BlockManager
import BallCore.Storage.SQLManager
import org.bukkit.plugin.Plugin
import BallCore.UI.Prompts

object Ferrobyte:
    val group = ItemGroup(
        NamespacedKey("ballcore", "ferrobyte"),
        ItemStack(Material.REDSTONE),
    )

    def registerItems()(using
        ir: ItemRegistry,
        bm: BlockManager,
        sql: SQLManager,
        p: Plugin,
        prompts: Prompts,
    ): Unit =
        val textProjectorResult = TextProjector.template.clone()
        textProjectorResult.setAmount(2)
        val textProjectorRecipe = ShapedRecipe(
            NamespacedKey("ballcore", "text_projector"),
            textProjectorResult,
        )
        textProjectorRecipe.shape(
            "FFF",
            "FSF",
            "FFF",
        )
        textProjectorRecipe.setIngredient(
            'F',
            ExactChoice(Tier2.ferrobyte.stack),
        )
        textProjectorRecipe.setIngredient('S', MaterialChoice(Tag.SIGNS))
        ir.addRecipe(textProjectorRecipe)
        ir.register(TextProjector())
