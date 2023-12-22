// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Gear

import BallCore.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment

object ManyullynGear:
    import BallCore.Alloys.Tier2.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        tools(
            Diamond,
            manyullyn.stack,
            manyullyn.name,
            manyullyn.id,
            DiamondToolSetCustomModelDatas.manyullyn,
            (Enchantment.DURABILITY, 3),
        )
        sword(
            Diamond,
            manyullyn.stack,
            manyullyn.name,
            manyullyn.id,
            DiamondToolSetCustomModelDatas.manyullyn,
            (Enchantment.DAMAGE_ALL, 2),
        )
