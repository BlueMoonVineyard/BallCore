// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Gear

import CivCubed.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment

object SunoGear:
    import CivCubed.Alloys.Tier2.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        tools(
            Diamond,
            suno.stack,
            suno.name,
            suno.id,
            DiamondToolSetCustomModelDatas.suno,
            (Enchantment.UNBREAKING, 1),
            (Enchantment.FORTUNE, 2),
        )
        sword(
            Diamond,
            suno.stack,
            suno.name,
            suno.id,
            DiamondToolSetCustomModelDatas.suno,
            (Enchantment.UNBREAKING, 1),
            (Enchantment.LOOTING, 2),
        )
