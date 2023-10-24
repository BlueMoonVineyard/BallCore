// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CustomItems

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.inventory.Recipe
import org.bukkit.Server
import org.bukkit.inventory.CookingRecipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe

object BasicItemRegistry:
    val persistenceKeyID = NamespacedKey("ballcore", "basic_item_registry_id")

class BasicItemRegistry(using s: Server) extends ItemRegistry:
    var itemMap = Map[NamespacedKey, CustomItem]()
    var recipeList = List[NamespacedKey]()
    def register(item: CustomItem): Unit =
        itemMap += item.id -> item
    def lookup(from: ItemStack): Option[CustomItem] =
        val meta = from.getItemMeta()
        if meta == null then
            return None
        val pdc = meta.getPersistentDataContainer()
        Option(pdc.getOrDefault(BasicItemRegistry.persistenceKeyID, PersistentDataType.STRING, null))
            .map(NamespacedKey.fromString)
            .flatMap(itemMap.get)
    def lookup(from: NamespacedKey): Option[CustomItem] =
        itemMap.get(from)
    def addRecipe(recipe: Recipe): Unit =
        if !s.addRecipe(recipe) then
            throw new Exception(s"failed to register ${recipe}")
        recipeList = getKey(recipe) :: recipeList
    def recipes(): List[NamespacedKey] =
        recipeList
    private def getKey(recipe: Recipe): NamespacedKey =
        recipe match
            case s: (ShapedRecipe | ShapelessRecipe | CookingRecipe[_]) => s.getKey()