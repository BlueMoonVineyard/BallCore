// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Gear

import CivCubed.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment

object SkyBronzeGear:
    import CivCubed.Alloys.Tier2.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        armor(
            Diamond,
            skyBronzeMorning.stack,
            skyBronzeMorning.name,
            skyBronzeMorning.id,
            DiamondToolSetCustomModelDatas.skyBronzeMorning,
            (Enchantment.PROTECTION, 2),
        )
        armor(
            Diamond,
            skyBronzeDay.stack,
            skyBronzeDay.name,
            skyBronzeDay.id,
            DiamondToolSetCustomModelDatas.skyBronzeDay,
            (Enchantment.FIRE_PROTECTION, 3),
            (Enchantment.FEATHER_FALLING, 2),
            (Enchantment.UNBREAKING, 2),
        )
        armor(
            Diamond,
            skyBronzeEvening.stack,
            skyBronzeEvening.name,
            skyBronzeEvening.id,
            DiamondToolSetCustomModelDatas.skyBronzeEvening,
            (Enchantment.BLAST_PROTECTION, 3),
            (Enchantment.FEATHER_FALLING, 2),
            (Enchantment.UNBREAKING, 2),
        )
        armor(
            Diamond,
            skyBronzeNight.stack,
            skyBronzeNight.name,
            skyBronzeNight.id,
            DiamondToolSetCustomModelDatas.skyBronzeNight,
            (Enchantment.PROJECTILE_PROTECTION, 3),
            (Enchantment.FEATHER_FALLING, 2),
            (Enchantment.UNBREAKING, 2),
        )
