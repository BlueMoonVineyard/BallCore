// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Ores

import CivCubed.CustomItems.{
    CustomItemStack,
    ItemGroup,
    ItemRegistry,
    PlainCustomItem,
}
import CivCubed.UI.Elements.*
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.{
    BlastingRecipe,
    FurnaceRecipe,
    ShapedRecipe,
    ShapelessRecipe,
}
import org.bukkit.{Material, NamespacedKey, Server}

import scala.util.chaining.*
import org.bukkit.inventory.ItemStack

/// helper class for managing custom model data numbers of the oretier ore types
enum OreTypes(val num: Int):
    case adamantite extends OreTypes(10)
    case arilla extends OreTypes(20)
    case mythril extends OreTypes(30)
    case orichalcum extends OreTypes(40)
    case palladium extends OreTypes(50)

enum OreTier:
    case Raw
    case Nugget
    case Ingot
    case Block

case class OreVariants(
    raw: CustomItemStack,
    nugget: CustomItemStack,
    ingot: CustomItemStack,
    block: CustomItemStack,
    id: String,
):
    def ore(tier: OreTier): CustomItemStack =
        tier match
            case OreTier.Raw => raw
            case OreTier.Nugget => nugget
            case OreTier.Ingot => ingot
            case OreTier.Block => block

    def register(
        group: ItemGroup
    )(using registry: ItemRegistry, serv: Server): Unit =
        OreTier.values.foreach { tier =>
            registry.register(Ore(group, tier, this))
        }

        def recipeKey(in: CustomItemStack) =
            NamespacedKey(
                group.key.getNamespace,
                group.key.getKey + "_" + in.id.getKey.toLowerCase(),
            )

        def blastKey(in: CustomItemStack) =
            NamespacedKey(
                group.key.getNamespace,
                group.key.getKey + "_blast_" + in.id.getKey.toLowerCase(),
            )

        def blockKey(in: CustomItemStack) =
            NamespacedKey(
                group.key.getNamespace,
                group.key.getKey + "_block_" + in.id.getKey.toLowerCase(),
            )

        def ingotFromBlockKey(in: CustomItemStack) =
            NamespacedKey(
                group.key.getNamespace,
                group.key.getKey + "_ingot_from_block_" + in.id.getKey
                    .toLowerCase(),
            )

        def nuggetKey(in: CustomItemStack) =
            NamespacedKey(
                group.key.getNamespace,
                group.key.getKey + "_nugget_" + in.id.getKey.toLowerCase(),
            )

        def ingotFromNuggetKey(in: CustomItemStack) =
            NamespacedKey(
                group.key.getNamespace,
                group.key.getKey + "_ingot_from_nugget_" + in.id.getKey
                    .toLowerCase(),
            )

        val initialYield = nugget.clone().tap(_.setAmount(6))

        val recipeTicks = 100
        val rawRecipe = FurnaceRecipe(
            recipeKey(raw),
            initialYield,
            ExactChoice(raw),
            0.0f,
            recipeTicks,
        )
        registry.addRecipe(rawRecipe)

        val rawRecipeB = BlastingRecipe(
            blastKey(raw),
            initialYield,
            ExactChoice(raw),
            0.0f,
            recipeTicks,
        )
        registry.addRecipe(rawRecipeB)

        val blockRecipe = ShapedRecipe(blockKey(block), block)
        blockRecipe.shape(
            "III",
            "III",
            "III",
        )
        blockRecipe.setIngredient('I', ExactChoice(ingot))
        registry.addRecipe(blockRecipe)

        val ingotFromBlockRecipe = ShapelessRecipe(
            ingotFromBlockKey(ingot),
            ingot.clone().tap(_.setAmount(9)),
        )
        ingotFromBlockRecipe.addIngredient(ExactChoice(block))
        registry.addRecipe(ingotFromBlockRecipe)

        val ingotFromNuggetRecipe =
            ShapedRecipe(ingotFromNuggetKey(ingot), ingot)
        ingotFromNuggetRecipe.shape(
            "NNN",
            "NNN",
            "NNN",
        )
        ingotFromNuggetRecipe.setIngredient('N', ExactChoice(nugget))
        registry.addRecipe(ingotFromNuggetRecipe)

        val nuggetRecipe =
            ShapelessRecipe(
                nuggetKey(nugget),
                nugget.clone().tap(_.setAmount(9)),
            )
        nuggetRecipe.addIngredient(ExactChoice(ingot))
        registry.addRecipe(nuggetRecipe)

object Helpers:
    def factory(
        id: String,
        num: Int,
        raw: Material,
        nugget: Material,
        ingot: Material,
        block: Material,
    ): OreVariants =
        OreVariants(
            withCustomModelData(
                CustomItemStack
                    .make(
                        NamespacedKey("civcubed", s"raw_$id"),
                        raw,
                        trans"items.$id.raw",
                    ),
                num + 0,
            ),
            withCustomModelData(
                CustomItemStack.make(
                    NamespacedKey("civcubed", s"${id}_nugget"),
                    nugget,
                    trans"items.$id.nugget",
                ),
                num + 1,
            ),
            withCustomModelData(
                CustomItemStack.make(
                    NamespacedKey("civcubed", s"${id}_ingot"),
                    ingot,
                    trans"items.$id.ingot",
                ),
                num + 2,
            ),
            withCustomModelData(
                CustomItemStack.make(
                    NamespacedKey("civcubed", s"${id}_block"),
                    block,
                    trans"items.$id.block",
                ),
                num + 3,
            ),
            id,
        )

    private def withCustomModelData(
        is: CustomItemStack,
        md: Int,
    ): CustomItemStack =
        val im = is.getItemMeta
        im.setCustomModelData(md)
        is.setItemMeta(im)
        is

    def ironLike(id: String, num: Int): OreVariants =
        factory(
            id,
            num,
            Material.RAW_IRON,
            Material.IRON_NUGGET,
            Material.IRON_INGOT,
            Material.IRON_BLOCK,
        )

    def register(group: ItemGroup, variants: OreVariants)(using
        registry: ItemRegistry,
        server: Server,
    ): Unit =
        variants.register(group)

    def register(group: ItemGroup, ms: CustomItemStack*)(using
        registry: ItemRegistry,
        server: Server,
    ): Unit =
        ms.foreach { x => registry.register(PlainCustomItem(group, x)) }

def register()(using ItemRegistry, Server): Unit =
    val oreTypes = List(
        ("adamantite", OreTypes.adamantite),
        ("arilla", OreTypes.arilla),
        ("mythril", OreTypes.mythril),
        ("orichalcum", OreTypes.orichalcum),
        ("palladium", OreTypes.palladium),
    )
    oreTypes.foreach { (name, kind) =>
        Helpers
            .ironLike(name, kind.num)
            .register(
                ItemGroup(
                    NamespacedKey("civcubed", "ores"),
                    ItemStack(Material.DIRT),
                )
            )
    }
