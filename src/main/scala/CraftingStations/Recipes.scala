package BallCore.CraftingStations

import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ItemStack

case class Recipe(
    name: String,
    inputs: List[(RecipeChoice, Int)],
    outputs: List[ItemStack],
    /// amount of player "work" needed to craft this recipe, in ticks
    ///
    /// a player can only dedicate "work" to one factory at a time,
    /// but they can do other stuff whilst working a factory
    work: Int,
)
