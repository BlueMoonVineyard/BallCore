// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Groups.GroupManager
import BallCore.UI.Prompts
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.Material
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.ItemStack
import BallCore.CustomItems.ItemRegistry
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Server
import BallCore.Beacons.CivBeaconManager

object Recipes:
    val kinds = List(
        (NamespacedKey("ballcore", "plumb_and_square_stone"), Material.STONE),
        (NamespacedKey("ballcore", "plumb_and_square_deepslate"), Material.DEEPSLATE),
        (NamespacedKey("ballcore", "plumb_and_square_iron"), Material.IRON_INGOT),
        (NamespacedKey("ballcore", "plumb_and_square_copper"), Material.COPPER_INGOT),
    )

object Reinforcements:
    def registerItems()(using registry: ItemRegistry, server: Server): Unit =
        registry.register(PlumbAndSquare())

        // plumb-and-square crafting registration

        Recipes.kinds.foreach { it =>
            val (key, mat) = it

            val doot = PlumbAndSquare.itemStack.clone()
            val recp = ShapelessRecipe(key, doot)
            recp.addIngredient(1, PlumbAndSquare.itemStack.getType())
            recp.addIngredient(1, mat)
            server.addRecipe(recp)
        }

    def registerBlockListener()(using server: Server, registry: ItemRegistry, plugin: JavaPlugin, cbm: CivBeaconManager, gm: GroupManager, holos: HologramManager, prompts: Prompts): Unit =
        server.getPluginManager().registerEvents(Listener(), plugin)

    def registerEntityListener()(using server: Server, registry: ItemRegistry, plugin: JavaPlugin, erm: EntityReinforcementManager, gm: GroupManager, holos: HologramManager): Unit =
        server.getPluginManager().registerEvents(EntityListener(), plugin)

    def register()(using registry: ItemRegistry, server: Server, plugin: JavaPlugin, cbm: CivBeaconManager, erm: EntityReinforcementManager, gm: GroupManager, holos: HologramManager, prompts: Prompts): Unit =
        registerItems()
        registerBlockListener()
        registerEntityListener()

