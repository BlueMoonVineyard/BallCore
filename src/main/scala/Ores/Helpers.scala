// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.CustomItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.Server
import BallCore.CustomItems.PlainCustomItem

enum OreTier:
    case Dust
    case Scraps
    case Depleted
    case Raw
    case Ingot
    case Block

case class OreVariants(
    dust: CustomItemStack,
    scraps: CustomItemStack,
    depleted: CustomItemStack,
    raw: CustomItemStack,
    ingot: CustomItemStack,
    block: CustomItemStack,
    name: String,
    id: String,
):
    def ore(tier: OreTier): CustomItemStack =
        tier match
            case OreTier.Dust => dust
            case OreTier.Scraps => scraps
            case OreTier.Depleted => depleted
            case OreTier.Raw => raw
            case OreTier.Ingot => ingot
            case OreTier.Block => block
    def register(group: ItemGroup, registry: ItemRegistry, serv: Server): Unit =
        OreTier.values.foreach { tier =>
            registry.register(Ore(group, tier, this))
        }
        def recipeKey(in: CustomItemStack) =
            NamespacedKey(group.key.getNamespace(), group.key.getKey() + "_" + in.id.getKey().toLowerCase())
        def blastKey(in: CustomItemStack) =
            NamespacedKey(group.key.getNamespace(), group.key.getKey() + "_blast_" + in.id.getKey().toLowerCase())
        def blockKey(in: CustomItemStack) =
            NamespacedKey(group.key.getNamespace(), group.key.getKey() + "_block_" + in.id.getKey().toLowerCase())
        def ingotKey(in: CustomItemStack) =
            NamespacedKey(group.key.getNamespace(), group.key.getKey() + "_ingot_" + in.id.getKey().toLowerCase())

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
            CustomItemStack.make(NamespacedKey("ballcore", s"${id}_dust"), m0, s"&r$name Dust"),
            CustomItemStack.make(NamespacedKey("ballcore", s"${id}_scraps"), m1, s"&r$name Scraps"),
            CustomItemStack.make(NamespacedKey("ballcore", s"depleted_${id}"), m1, s"&rDepleted $name"),
            CustomItemStack.make(NamespacedKey("ballcore", s"raw_${id}"), m1, s"&rRaw $name"),
            CustomItemStack.make(NamespacedKey("ballcore", s"${id}_ingot"), m2, s"&r$name Ingot"),
            CustomItemStack.make(NamespacedKey("ballcore", s"${id}_block"), m3, s"&r$name Block"),
            name,
            id,
        )

    def ironLike(id: String, name: String): OreVariants =
        factory(id, name, Material.SUGAR, Material.RAW_IRON, Material.IRON_INGOT, Material.IRON_BLOCK)
    def goldLike(id: String, name: String): OreVariants =
        factory(id, name, Material.GLOWSTONE, Material.RAW_GOLD, Material.GOLD_INGOT, Material.GOLD_BLOCK)
    def copperLike(id: String, name: String): OreVariants =
        factory(id, name, Material.GUNPOWDER, Material.RAW_COPPER, Material.COPPER_INGOT, Material.COPPER_BLOCK)
    def register(group: ItemGroup, variants: OreVariants)(using registry: ItemRegistry, server: Server) =
        variants.register(group, registry, server)
    def register(group: ItemGroup, ms: CustomItemStack*)(using registry: ItemRegistry, server: Server) =
        ms.foreach{ x => registry.register(PlainCustomItem(group, x)) }

