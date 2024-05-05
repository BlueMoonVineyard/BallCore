package CivCubed.Ferrobyte

import CivCubed.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import CivCubed.CustomItems.ItemRegistry
import org.bukkit.inventory.ShapedRecipe
import CivCubed.Alloys.Tier2
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.Tag
import CivCubed.CustomItems.BlockManager
import CivCubed.Storage.SQLManager
import org.bukkit.plugin.Plugin
import CivCubed.UI.Prompts
import CivCubed.Storage.KeyVal

object Ferrobyte:
    val group = ItemGroup(
        NamespacedKey("civcubed", "ferrobyte"),
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
            NamespacedKey("civcubed", "text_projector"),
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
            NamespacedKey("civcubed", "backpack"),
            Backpack.template.clone(),
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
