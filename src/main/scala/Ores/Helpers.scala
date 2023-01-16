package BallCore.Ores

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType

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
):
    def ore(tier: OreTier): SlimefunItemStack =
        tier match
            case OreTier.Dust => dust
            case OreTier.Scraps => scraps
            case OreTier.Depleted => depleted
            case OreTier.Raw => raw
            case OreTier.Ingot => ingot
            case OreTier.Block => block

object Helpers:
    def factory(id: String, name: String, m0: Material, m1: Material, m2: Material, m3: Material): OreVariants =
        OreVariants(
            SlimefunItemStack(s"BC_${id}_DUST", m0, s"&r$name Dust"),
            SlimefunItemStack(s"BC_${id}_SCRAPS", m1, s"&r$name Scraps"),
            SlimefunItemStack(s"BC_DEPLETED_${id}", m1, s"&rDepleted $name"),
            SlimefunItemStack(s"BC_RAW_${id}", m1, s"&rRaw $name"),
            SlimefunItemStack(s"BC_${id}_INGOT", m2, s"&r$name Ingot"),
            SlimefunItemStack(s"BC_${id}_BLOCK", m3, s"&r$name Block"),
        )

    def ironLike(id: String, name: String): OreVariants =
        factory(id, name, Material.SUGAR, Material.RAW_IRON, Material.IRON_INGOT, Material.IRON_BLOCK)
    def goldLike(id: String, name: String): OreVariants =
        factory(id, name, Material.GLOWSTONE, Material.RAW_GOLD, Material.GOLD_INGOT, Material.GOLD_BLOCK)
    def copperLike(id: String, name: String): OreVariants =
        factory(id, name, Material.GUNPOWDER, Material.RAW_COPPER, Material.COPPER_INGOT, Material.COPPER_BLOCK)
    def register(group: ItemGroup, variants: OreVariants)(using plugin: SlimefunAddon) =
        List(variants.dust, variants.scraps, variants.depleted, variants.raw, variants.ingot, variants.block)
        .foreach{ new SlimefunItem(group, _, RecipeType.NULL, null).register(plugin) }
    def register(group: ItemGroup, ms: SlimefunItemStack*)(using plugin: SlimefunAddon) =
        ms.foreach{ new SlimefunItem(group, _, RecipeType.NULL, null).register(plugin) }

