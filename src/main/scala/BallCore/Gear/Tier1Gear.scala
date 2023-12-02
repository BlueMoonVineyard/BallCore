// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Gear

import BallCore.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment

object Tier1Gear:
    import BallCore.Alloys.Tier1.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        tools(
            Iron,
            pallalumin.stack,
            pallalumin.name,
            pallalumin.id,
            IronToolSetCustomModelDatas.pallalumin,
            (Enchantment.DURABILITY, 3),
            (Enchantment.DIG_SPEED, 1),
        )
        sword(
            Iron,
            pallalumin.stack,
            pallalumin.name,
            pallalumin.id,
            IronToolSetCustomModelDatas.pallalumin,
            (Enchantment.DURABILITY, 3),
            (Enchantment.DAMAGE_ALL, 2),
        )
        armor(
            Iron,
            pallalumin.stack,
            pallalumin.name,
            pallalumin.id,
            IronToolSetCustomModelDatas.pallalumin,
            (Enchantment.DURABILITY, 4),
        )

        tools(
            Iron,
            bronze.stack,
            bronze.name,
            bronze.id,
            IronToolSetCustomModelDatas.bronze,
            (Enchantment.DURABILITY, 2),
            (Enchantment.DIG_SPEED, 2),
        )
        sword(
            Iron,
            bronze.stack,
            bronze.name,
            bronze.id,
            IronToolSetCustomModelDatas.bronze,
            (Enchantment.DURABILITY, 2),
            (Enchantment.SWEEPING_EDGE, 1),
            (Enchantment.KNOCKBACK, 1),
        )
        armor(
            Iron,
            bronze.stack,
            bronze.name,
            bronze.id,
            IronToolSetCustomModelDatas.bronze,
            (Enchantment.DURABILITY, 1),
            (Enchantment.THORNS, 1),
        )

        tools(
            Iron,
            magnox.stack,
            magnox.name,
            magnox.id,
            IronToolSetCustomModelDatas.magnox,
            (Enchantment.DURABILITY, 1),
            (Enchantment.DIG_SPEED, 4),
        )
        sword(
            Iron,
            magnox.stack,
            magnox.name,
            magnox.id,
            IronToolSetCustomModelDatas.magnox,
            (Enchantment.DURABILITY, 2),
            (Enchantment.LOOT_BONUS_MOBS, 1),
            (Enchantment.KNOCKBACK, 1),
        )
        armor(
            Iron,
            magnox.stack,
            magnox.name,
            magnox.id,
            IronToolSetCustomModelDatas.magnox,
            (Enchantment.DURABILITY, 1),
            (Enchantment.OXYGEN, 1),
            (Enchantment.WATER_WORKER, 1),
        )

        tools(
            Iron,
            gildedIron.stack,
            gildedIron.name,
            gildedIron.id,
            IronToolSetCustomModelDatas.gildedIron,
            (Enchantment.DURABILITY, 2),
            (Enchantment.DIG_SPEED, 2),
        )
        sword(
            Iron,
            gildedIron.stack,
            gildedIron.name,
            gildedIron.id,
            IronToolSetCustomModelDatas.gildedIron,
            (Enchantment.DURABILITY, 4),
            (Enchantment.DAMAGE_ALL, 2),
        )
        armor(
            Iron,
            gildedIron.stack,
            gildedIron.name,
            gildedIron.id,
            IronToolSetCustomModelDatas.gildedIron,
            (Enchantment.DURABILITY, 2),
            (Enchantment.PROTECTION_ENVIRONMENTAL, 1),
        )
