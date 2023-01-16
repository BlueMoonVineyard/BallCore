package BallCore.Ores

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType

class Ore(ig: ItemGroup, oreTier: OreTier, vars: OreVariants)
    extends SlimefunItem(ig, vars.ore(oreTier), RecipeType.NULL, null):

    val tier = oreTier
    val variants = vars
