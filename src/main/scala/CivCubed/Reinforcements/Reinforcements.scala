// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Reinforcements

import CivCubed.Beacons.CivBeaconManager
import CivCubed.CustomItems.ItemRegistry
import CivCubed.Groups.GroupManager
import CivCubed.Storage.SQLManager
import CivCubed.UI.Prompts
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import CivCubed.Fingerprints.FingerprintManager
import CivCubed.WebHooks.WebHookManager
import CivCubed.PrimeTime.PrimeTimeManager
import CivCubed.CustomItems.BlockManager
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.Material
import CivCubed.Sigils.BattleManager
import CivCubed.NoodleEditor.NoodleManager
import CivCubed.NoodleEditor.DelinquencyManager

object Reinforcements:
    private def registerBlockListener()(using
        server: Server,
        plugin: JavaPlugin,
        cbm: CivBeaconManager,
        gm: GroupManager,
        sql: SQLManager,
        busts: BustThroughTracker,
        fingerprints: FingerprintManager,
        webhooks: WebHookManager,
        primeTime: PrimeTimeManager,
        blockManager: BlockManager,
        ir: ItemRegistry,
        battles: BattleManager,
        noodle: NoodleManager,
        delinquency: DelinquencyManager,
    ): Unit =
        server.getPluginManager.registerEvents(Listener(), plugin)

    private def registerEntityListener()(using
        server: Server,
        plugin: JavaPlugin,
        erm: EntityReinforcementManager,
        gm: GroupManager,
        sql: SQLManager,
    ): Unit =
        server.getPluginManager.registerEvents(EntityListener(), plugin)

    private def registerPlumbAndSquare()(using
        server: Server,
        registry: ItemRegistry,
        plugin: JavaPlugin,
        prompts: Prompts,
        gm: GroupManager,
        erm: EntityReinforcementManager,
        sql: SQLManager,
    ): Unit =
        registry.register(PlumbAndSquare())

        // plumb-and-square crafting registration

        val rezept = ShapedRecipe(
            PlumbAndSquare.mainRecipe,
            PlumbAndSquare.itemStack.clone(),
        )
        rezept.shape(
            "TTT",
            " RT",
            "  T",
        )
        rezept.setIngredient('T', Material.STICK)
        rezept.setIngredient('R', Material.STRING)
        registry.addRecipe(rezept)

        PlumbAndSquare.kinds.foreach { it =>
            val (key, mat) = it

            val doot = PlumbAndSquare.itemStack.clone()
            val recp = ShapelessRecipe(key, doot)
            recp.addIngredient(1, PlumbAndSquare.itemStack.getType())
            recp.addIngredient(1, mat)
            registry.addRecipe(recp)
        }
        server.getPluginManager.registerEvents(PlumbAndSquareListener(), plugin)

    def register()(using
        registry: ItemRegistry,
        server: Server,
        plugin: JavaPlugin,
        cbm: CivBeaconManager,
        erm: EntityReinforcementManager,
        gm: GroupManager,
        holos: HologramManager,
        prompts: Prompts,
        sql: SQLManager,
        busts: BustThroughTracker,
        fingerprints: FingerprintManager,
        webhooks: WebHookManager,
        primeTime: PrimeTimeManager,
        blockManager: BlockManager,
        battles: BattleManager,
        noodle: NoodleManager,
        delinquency: DelinquencyManager,
    ): Unit =
        registerBlockListener()
        registerEntityListener()
        registerPlumbAndSquare()
