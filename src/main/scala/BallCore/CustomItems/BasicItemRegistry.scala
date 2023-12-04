// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import org.bukkit.inventory.*
import org.bukkit.persistence.PersistentDataType
import org.bukkit.{NamespacedKey, Server}

object BasicItemRegistry:
    val persistenceKeyID: NamespacedKey =
        NamespacedKey("ballcore", "basic_item_registry_id")

class BasicItemRegistry(using s: Server) extends ItemRegistry:
    private var itemMap: Map[NamespacedKey, CustomItem] =
        Map[NamespacedKey, CustomItem]()
    private var recipeList: List[NamespacedKey] = List[NamespacedKey]()

    def items(): List[CustomItem] =
        itemMap.values.toList

    def register(item: CustomItem): Unit =
        itemMap += item.id -> item

    def lookup(from: ItemStack): Option[CustomItem] =
        val meta = from.getItemMeta
        if meta == null then return None

        val pdc = meta.getPersistentDataContainer
        Option(
            pdc.getOrDefault(
                BasicItemRegistry.persistenceKeyID,
                PersistentDataType.STRING,
                null,
            )
        )
            .map(NamespacedKey.fromString)
            .flatMap(itemMap.get)

    def lookup(from: NamespacedKey): Option[CustomItem] =
        itemMap.get(from)

    def addRecipe(recipe: Recipe): Unit =
        if !s.addRecipe(recipe) then
            throw new Exception(s"failed to register $recipe")
        recipeList = getKey(recipe) :: recipeList

    def recipes(): List[NamespacedKey] =
        recipeList

    // noinspection ScalaUnnecessaryParentheses
    private def getKey(recipe: Recipe): NamespacedKey =
        recipe match
            case s: (ShapedRecipe | ShapelessRecipe | CookingRecipe[_] |
                    StonecuttingRecipe) =>
                s.getKey()
