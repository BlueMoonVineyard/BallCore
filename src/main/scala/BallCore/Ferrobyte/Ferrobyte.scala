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
import BallCore.Storage.KeyVal

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
        kv: KeyVal,
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

        val backpackRecipe = ShapedRecipe(
            NamespacedKey("ballcore", "backpack"),
            Backpack.template.clone()
        )
        backpackRecipe.shape(
            "FFF",
            "FCF",
            "FFF",
        )
        backpackRecipe.setIngredient('F', ExactChoice(Tier2.ferrobyte.stack))
        backpackRecipe.setIngredient('C', Material.CHEST)
        ir.addRecipe(backpackRecipe)
        ir.register(Backpack())
        p.getServer.getPluginManager.registerEvents(BackpackListener(), p)
