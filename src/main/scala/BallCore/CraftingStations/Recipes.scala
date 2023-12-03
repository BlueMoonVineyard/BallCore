// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.{ItemStack}
import org.bukkit.Material
import BallCore.CustomItems.CustomItemStack
import org.bukkit.Tag
import BallCore.CustomItems.ItemRegistry

enum RecipeIngredient:
    case Vanilla(oneOf: Material*)
    case Custom(oneOf: CustomItemStack*)
    case TagList(tag: Tag[Material])

    def test(stack: ItemStack)(using ir: ItemRegistry): Boolean =
        this match
            case Vanilla(oneOf: _*) =>
                oneOf.contains(stack.getType())
            case Custom(oneOf: _*) =>
                oneOf.exists { possibility =>
                    Some(possibility.id) == ir.lookup(stack).map(_.id)
                }
            case TagList(tag) =>
                tag.isTagged(stack.getType())

case class Recipe(
    name: String,
    inputs: List[(RecipeIngredient, Int)],
    outputs: List[(ItemStack, Int)],
    /// amount of player "work" needed to craft this recipe, in ticks
    ///
    /// a player can only dedicate "work" to one factory at a time,
    /// but they can do other stuff whilst working a factory
    work: Int,
    minimumPlayersRequiredToWork: Int,
)

case class Job(
    factory: Block,
    recipe: Recipe,
    currentWork: Int,
    workedBy: List[Player],
)
