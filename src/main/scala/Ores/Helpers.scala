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
import scala.util.chaining._

/// helper class for managing custom model data numbers of the oretier ore types
enum OreTypes(val num: Int):
    case iron extends OreTypes(10)
    case tin extends OreTypes(20)
    case aluminum extends OreTypes(30)
    case zinc extends OreTypes(40)
    case silver extends OreTypes(50)
    case sillicon extends OreTypes(60)
    case cobalt extends OreTypes(70)
    case lead extends OreTypes(80)

    case copper extends OreTypes(10)
    case orichalcum extends OreTypes(20)
    case hihiirogane extends OreTypes(30)
    case meteorite extends OreTypes(40)

    case gold extends OreTypes(10)
    case sulfur extends OreTypes(20)
    case palladium extends OreTypes(30)
    case magnesium extends OreTypes(40)

enum OreTier:
    case Dust
    case Scraps
    case Depleted
    case Raw
    case Nugget
    case Ingot
    case Block

case class OreVariants(
    dust: CustomItemStack,
    scraps: CustomItemStack,
    depleted: CustomItemStack,
    raw: CustomItemStack,
    nugget: CustomItemStack,
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
            case OreTier.Nugget => nugget
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
        def ingotFromBlockKey(in: CustomItemStack) =
            NamespacedKey(group.key.getNamespace(), group.key.getKey() + "_ingot_from_block_" + in.id.getKey().toLowerCase())
        def nuggetKey(in: CustomItemStack) =
            NamespacedKey(group.key.getNamespace(), group.key.getKey() + "_nugget_" + in.id.getKey().toLowerCase())
        def ingotFromNuggetKey(in: CustomItemStack) =
            NamespacedKey(group.key.getNamespace(), group.key.getKey() + "_ingot_from_nugget_" + in.id.getKey().toLowerCase())

        val initialYield = nugget.clone().tap(_.setAmount(6))
        val scrapsYield = nugget.clone().tap(_.setAmount(4))

        val recipeTicks = 100
        val rawRecipe = FurnaceRecipe(recipeKey(raw), initialYield, ExactChoice(raw), 0.0f, recipeTicks)
        val depletedRecipe = FurnaceRecipe(recipeKey(depleted), scrapsYield, ExactChoice(depleted), 0.0f, recipeTicks)
        val scrapsRecipe = FurnaceRecipe(recipeKey(scraps), scrapsYield, ExactChoice(scraps), 0.0f, recipeTicks)
        val dustRecipe = FurnaceRecipe(recipeKey(dust), scrapsYield, ExactChoice(dust), 0.0f, recipeTicks)
        serv.addRecipe(rawRecipe)
        serv.addRecipe(depletedRecipe)
        serv.addRecipe(scrapsRecipe)
        serv.addRecipe(dustRecipe)

        val rawRecipeB = BlastingRecipe(blastKey(raw), initialYield, ExactChoice(raw), 0.0f, recipeTicks)
        val depletedRecipeB = BlastingRecipe(blastKey(depleted), scrapsYield, ExactChoice(depleted), 0.0f, recipeTicks)
        val scrapsRecipeB = BlastingRecipe(blastKey(scraps), scrapsYield, ExactChoice(scraps), 0.0f, recipeTicks)
        val dustRecipeB = BlastingRecipe(blastKey(dust), scrapsYield, ExactChoice(dust), 0.0f, recipeTicks)
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

        val ingotFromBlockRecipe = ShapelessRecipe(ingotFromBlockKey(ingot), ingot.clone().tap(_.setAmount(9)))
        ingotFromBlockRecipe.addIngredient(ExactChoice(block))
        serv.addRecipe(ingotFromBlockRecipe)

        val ingotFromNuggetRecipe = ShapedRecipe(ingotFromNuggetKey(ingot), ingot)
        ingotFromNuggetRecipe.shape(
            "NNN",
            "NNN",
            "NNN",
        )
        ingotFromNuggetRecipe.setIngredient('N', ExactChoice(nugget))
        serv.addRecipe(ingotFromNuggetRecipe)

        val nuggetRecipe = ShapelessRecipe(nuggetKey(nugget), nugget.clone().tap(_.setAmount(9)))
        nuggetRecipe.addIngredient(ExactChoice(ingot))
        serv.addRecipe(nuggetRecipe)

object Helpers:
    def factory(id: String, name: String, num: Int, m0: Material, m1: Material, m2: Material, m3: Material, m4: Material): OreVariants =
        OreVariants(
            withCustomModelData(CustomItemStack.make(NamespacedKey("ballcore", s"${id}_dust"), m0, s"&r$name Dust"), num + 0),
            withCustomModelData(CustomItemStack.make(NamespacedKey("ballcore", s"${id}_scraps"), m1, s"&r$name Scraps"), num + 1),
            withCustomModelData(CustomItemStack.make(NamespacedKey("ballcore", s"depleted_${id}"), m1, s"&rDepleted $name"), num + 2),
            withCustomModelData(CustomItemStack.make(NamespacedKey("ballcore", s"raw_${id}"), m1, s"&rRaw $name"), num + 3),
            withCustomModelData(CustomItemStack.make(NamespacedKey("ballcore", s"${id}_nugget"), m2, s"&r$name Nugget"), num + 4),
            withCustomModelData(CustomItemStack.make(NamespacedKey("ballcore", s"${id}_ingot"), m3, s"&r$name Ingot"), num + 5),
            withCustomModelData(CustomItemStack.make(NamespacedKey("ballcore", s"${id}_block"), m4, s"&r$name Block"), num + 6),
            name,
            id,
        )

    def withCustomModelData(is: CustomItemStack, md: Int): CustomItemStack =
        val im = is.getItemMeta()
        im.setCustomModelData(md)
        is.setItemMeta(im)
        is
    def ironLike(id: String, name: String, num: Int): OreVariants =
        factory(id, name, num, Material.SUGAR, Material.RAW_IRON, Material.IRON_NUGGET, Material.IRON_INGOT, Material.IRON_BLOCK)
    def goldLike(id: String, name: String, num: Int): OreVariants =
        factory(id, name, num, Material.GLOWSTONE, Material.RAW_GOLD, Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK)
    def copperLike(id: String, name: String, num: Int): OreVariants =
        factory(id, name, num, Material.GUNPOWDER, Material.RAW_COPPER, Material.IRON_NUGGET, Material.COPPER_INGOT, Material.COPPER_BLOCK)
    def register(group: ItemGroup, variants: OreVariants)(using registry: ItemRegistry, server: Server) =
        variants.register(group, registry, server)
    def register(group: ItemGroup, ms: CustomItemStack*)(using registry: ItemRegistry, server: Server) =
        ms.foreach{ x => registry.register(PlainCustomItem(group, x)) }

