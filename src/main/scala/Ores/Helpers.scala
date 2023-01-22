package BallCore.Ores

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import org.bukkit.NamespacedKey
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe

enum OreTier:
    case Dust
    case Scraps
    case Depleted
    case Raw
    case Ingot
    case Block

case class OreVariants(
    dust: SlimefunItemStack,
    scraps: SlimefunItemStack,
    depleted: SlimefunItemStack,
    raw: SlimefunItemStack,
    ingot: SlimefunItemStack,
    block: SlimefunItemStack,
    name: String,
    id: String,
):
    def ore(tier: OreTier): SlimefunItemStack =
        tier match
            case OreTier.Dust => dust
            case OreTier.Scraps => scraps
            case OreTier.Depleted => depleted
            case OreTier.Raw => raw
            case OreTier.Ingot => ingot
            case OreTier.Block => block
    def register(group: ItemGroup, plugin: SlimefunAddon): Unit =
        OreTier.values.foreach { tier =>
            new Ore(group, tier, this).register(plugin)
        }
        def recipeKey(in: SlimefunItemStack) =
            NamespacedKey(group.getKey().getNamespace(), group.getKey().getKey() + "_" + in.getItemId().toLowerCase())
        def blastKey(in: SlimefunItemStack) =
            NamespacedKey(group.getKey().getNamespace(), group.getKey().getKey() + "_blast_" + in.getItemId().toLowerCase())
        def blockKey(in: SlimefunItemStack) =
            NamespacedKey(group.getKey().getNamespace(), group.getKey().getKey() + "_block_" + in.getItemId().toLowerCase())
        def ingotKey(in: SlimefunItemStack) =
            NamespacedKey(group.getKey().getNamespace(), group.getKey().getKey() + "_ingot_" + in.getItemId().toLowerCase())

        val serv = plugin.getJavaPlugin().getServer()
        val recipeTicks = 100
        val rawRecipe = FurnaceRecipe(recipeKey(raw), ingot, ExactChoice(raw), 0.0f, recipeTicks)
        val depletedRecipe = FurnaceRecipe(recipeKey(depleted), ingot, ExactChoice(depleted), 0.0f, recipeTicks)
        val scrapsRecipe = FurnaceRecipe(recipeKey(scraps), ingot, ExactChoice(scraps), 0.0f, recipeTicks)
        val dustRecipe = FurnaceRecipe(recipeKey(dust), ingot, ExactChoice(dust), 0.0f, recipeTicks)
        serv.addRecipe(rawRecipe)
        serv.addRecipe(depletedRecipe)
        serv.addRecipe(scrapsRecipe)
        serv.addRecipe(dustRecipe)

        val rawRecipeB = BlastingRecipe(blastKey(raw), ingot, ExactChoice(raw), 0.0f, recipeTicks)
        val depletedRecipeB = BlastingRecipe(blastKey(depleted), ingot, ExactChoice(depleted), 0.0f, recipeTicks)
        val scrapsRecipeB = BlastingRecipe(blastKey(scraps), ingot, ExactChoice(scraps), 0.0f, recipeTicks)
        val dustRecipeB = BlastingRecipe(blastKey(dust), ingot, ExactChoice(dust), 0.0f, recipeTicks)
        serv.addRecipe(rawRecipeB)
        serv.addRecipe(depletedRecipeB)
        serv.addRecipe(scrapsRecipeB)
        serv.addRecipe(dustRecipeB)

        val blockRecipe = ShapedRecipe(blockKey(block), block)
        blockRecipe.shape(
            "III",
            "III",
            "III",
        )
        blockRecipe.setIngredient('I', ExactChoice(ingot))
        serv.addRecipe(blockRecipe)

        val outStack = ingot.clone()
        outStack.setAmount(9)
        val ingotRecipe = ShapelessRecipe(ingotKey(ingot), outStack)
        ingotRecipe.addIngredient(ExactChoice(block))
        serv.addRecipe(ingotRecipe)

object Helpers:
    def factory(id: String, name: String, m0: Material, m1: Material, m2: Material, m3: Material): OreVariants =
        OreVariants(
            SlimefunItemStack(s"BC_${id}_DUST", m0, s"&r$name Dust"),
            SlimefunItemStack(s"BC_${id}_SCRAPS", m1, s"&r$name Scraps"),
            SlimefunItemStack(s"BC_DEPLETED_${id}", m1, s"&rDepleted $name"),
            SlimefunItemStack(s"BC_RAW_${id}", m1, s"&rRaw $name"),
            SlimefunItemStack(s"BC_${id}_INGOT", m2, s"&r$name Ingot"),
            SlimefunItemStack(s"BC_${id}_BLOCK", m3, s"&r$name Block"),
            name,
            id,
        )

    def ironLike(id: String, name: String): OreVariants =
        factory(id, name, Material.SUGAR, Material.RAW_IRON, Material.IRON_INGOT, Material.IRON_BLOCK)
    def goldLike(id: String, name: String): OreVariants =
        factory(id, name, Material.GLOWSTONE, Material.RAW_GOLD, Material.GOLD_INGOT, Material.GOLD_BLOCK)
    def copperLike(id: String, name: String): OreVariants =
        factory(id, name, Material.GUNPOWDER, Material.RAW_COPPER, Material.COPPER_INGOT, Material.COPPER_BLOCK)
    def register(group: ItemGroup, variants: OreVariants)(using plugin: SlimefunAddon) =
        variants.register(group, plugin)
    def register(group: ItemGroup, ms: SlimefunItemStack*)(using plugin: SlimefunAddon) =
        ms.foreach{ new SlimefunItem(group, _, RecipeType.NULL, null).register(plugin) }

