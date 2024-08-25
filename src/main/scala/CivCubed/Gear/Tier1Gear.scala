// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Gear

import CivCubed.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment

object Tier1Gear:
    import CivCubed.Alloys.Tier1.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        tools(
            Iron,
            pallalumin.stack,
            pallalumin.name,
            pallalumin.id,
            IronToolSetCustomModelDatas.pallalumin,
            (Enchantment.UNBREAKING, 3),
            (Enchantment.EFFICIENCY, 1),
        )
        sword(
            Iron,
            pallalumin.stack,
            pallalumin.name,
            pallalumin.id,
            IronToolSetCustomModelDatas.pallalumin,
            (Enchantment.UNBREAKING, 3),
            (Enchantment.SHARPNESS, 2),
        )
        armor(
            Iron,
            pallalumin.stack,
            pallalumin.name,
            pallalumin.id,
            IronToolSetCustomModelDatas.pallalumin,
            (Enchantment.UNBREAKING, 4),
        )

        tools(
            Iron,
            bronze.stack,
            bronze.name,
            bronze.id,
            IronToolSetCustomModelDatas.bronze,
            (Enchantment.UNBREAKING, 2),
            (Enchantment.EFFICIENCY, 2),
        )
        sword(
            Iron,
            bronze.stack,
            bronze.name,
            bronze.id,
            IronToolSetCustomModelDatas.bronze,
            (Enchantment.UNBREAKING, 2),
            (Enchantment.SWEEPING_EDGE, 1),
            (Enchantment.KNOCKBACK, 1),
        )
        armor(
            Iron,
            bronze.stack,
            bronze.name,
            bronze.id,
            IronToolSetCustomModelDatas.bronze,
            (Enchantment.UNBREAKING, 1),
            (Enchantment.THORNS, 1),
        )

        tools(
            Iron,
            magnox.stack,
            magnox.name,
            magnox.id,
            IronToolSetCustomModelDatas.magnox,
            (Enchantment.UNBREAKING, 1),
            (Enchantment.EFFICIENCY, 4),
        )
        sword(
            Iron,
            magnox.stack,
            magnox.name,
            magnox.id,
            IronToolSetCustomModelDatas.magnox,
            (Enchantment.UNBREAKING, 2),
            (Enchantment.LOOTING, 1),
            (Enchantment.KNOCKBACK, 1),
        )
        armor(
            Iron,
            magnox.stack,
            magnox.name,
            magnox.id,
            IronToolSetCustomModelDatas.magnox,
            (Enchantment.UNBREAKING, 1),
            (Enchantment.RESPIRATION, 1),
            (Enchantment.AQUA_AFFINITY, 1),
        )

        tools(
            Iron,
            gildedIron.stack,
            gildedIron.name,
            gildedIron.id,
            IronToolSetCustomModelDatas.gildedIron,
            (Enchantment.UNBREAKING, 2),
            (Enchantment.EFFICIENCY, 2),
        )
        sword(
            Iron,
            gildedIron.stack,
            gildedIron.name,
            gildedIron.id,
            IronToolSetCustomModelDatas.gildedIron,
            (Enchantment.UNBREAKING, 4),
            (Enchantment.SHARPNESS, 2),
        )
        armor(
            Iron,
            gildedIron.stack,
            gildedIron.name,
            gildedIron.id,
            IronToolSetCustomModelDatas.gildedIron,
            (Enchantment.UNBREAKING, 2),
            (Enchantment.PROTECTION, 1),
        )
