// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Gear

import BallCore.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment

object SunoGear:
    import BallCore.Alloys.Tier2.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        tools(
            Diamond,
            suno.stack,
            suno.name,
            suno.id,
            DiamondToolSetCustomModelDatas.suno,
            (Enchantment.DURABILITY, 1),
            (Enchantment.LOOT_BONUS_BLOCKS, 2),
        )
        sword(
            Diamond,
            suno.stack,
            suno.name,
            suno.id,
            DiamondToolSetCustomModelDatas.suno,
            (Enchantment.DURABILITY, 1),
            (Enchantment.LOOT_BONUS_MOBS, 2),
        )
