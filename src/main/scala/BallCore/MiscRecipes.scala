package BallCore

import BallCore.CustomItems.ItemRegistry
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material

object MiscRecipes:
    def register()(using ir: ItemRegistry): Unit =
        val saddleRecipe = ShapedRecipe(
            NamespacedKey("ballcore", "saddle"),
            ItemStack(Material.SADDLE),
        )
        saddleRecipe.shape(
            "LLL",
            "LSL",
        )
        saddleRecipe.setIngredient('L', Material.LEATHER)
        saddleRecipe.setIngredient('S', Material.STICK)
        ir.addRecipe(saddleRecipe)
