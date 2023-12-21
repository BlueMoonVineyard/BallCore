// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Gear

import BallCore.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment

object SkyBronzeGear:
    import BallCore.Alloys.Tier2.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        armor(
            Diamond,
            skyBronzeMorning.stack,
            skyBronzeMorning.name,
            skyBronzeMorning.id,
            DiamondToolSetCustomModelDatas.skyBronzeMorning,
            (Enchantment.PROTECTION_ENVIRONMENTAL, 2),
        )
        armor(
            Diamond,
            skyBronzeDay.stack,
            skyBronzeDay.name,
            skyBronzeDay.id,
            DiamondToolSetCustomModelDatas.skyBronzeDay,
            (Enchantment.PROTECTION_FIRE, 3),
            (Enchantment.PROTECTION_FALL, 2),
            (Enchantment.DURABILITY, 2),
        )
        armor(
            Diamond,
            skyBronzeEvening.stack,
            skyBronzeEvening.name,
            skyBronzeEvening.id,
            DiamondToolSetCustomModelDatas.skyBronzeEvening,
            (Enchantment.PROTECTION_EXPLOSIONS, 3),
            (Enchantment.PROTECTION_FALL, 2),
            (Enchantment.DURABILITY, 2),
        )
        armor(
            Diamond,
            skyBronzeNight.stack,
            skyBronzeNight.name,
            skyBronzeNight.id,
            DiamondToolSetCustomModelDatas.skyBronzeNight,
            (Enchantment.PROTECTION_PROJECTILE, 3),
            (Enchantment.PROTECTION_FALL, 2),
            (Enchantment.DURABILITY, 2),
        )
