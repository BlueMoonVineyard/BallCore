// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Gear

import org.bukkit.enchantments.Enchantment
import BallCore.CustomItems.ItemRegistry
import org.bukkit.Server

object QuadrantGear:
    import BallCore.Ores.QuadrantOres
    import BallCore.Ores.QuadrantOres.ItemStacks._
    import ToolSet._
    import Gear._

    def registerItems()(using registry: ItemRegistry, server: Server): Unit =
        tools(Iron, iron.ingot, iron.name, iron.id, (Enchantment.DURABILITY, 1))
        sword(Iron, iron.ingot, iron.name, iron.id, (Enchantment.DURABILITY, 1), (Enchantment.DAMAGE_ALL, 1))
        armor(Iron, iron.ingot, iron.name, iron.id, (Enchantment.DURABILITY, 1))

        tools(Gold, gold.ingot, gold.name, gold.id, (Enchantment.DURABILITY, 7))
        sword(Gold, gold.ingot, gold.name, gold.id, (Enchantment.DURABILITY, 4), (Enchantment.DAMAGE_ALL, 1))
        armor(Gold, gold.ingot, gold.name, gold.id, (Enchantment.DURABILITY, 4), (Enchantment.PROTECTION_ENVIRONMENTAL, 1))

        tools(Iron, copper.ingot, copper.name, copper.id, (Enchantment.DURABILITY, 2))
        sword(Iron, copper.ingot, copper.name, copper.id, (Enchantment.DURABILITY, 2), (Enchantment.SWEEPING_EDGE, 1))
        armor(Iron, copper.ingot, copper.name, copper.id, (Enchantment.DURABILITY, 2))

        tools(Iron, tin.ingot, tin.name, tin.id, (Enchantment.DIG_SPEED, 1), (Enchantment.DURABILITY, 1))
        sword(Iron, tin.ingot, tin.name, tin.id, (Enchantment.KNOCKBACK, 1), (Enchantment.DURABILITY, 1))
        armor(Iron, tin.ingot, tin.name, tin.id, (Enchantment.THORNS, 1))

        tools(Gold, sulfur.ingot, sulfur.name, sulfur.id, (Enchantment.DIG_SPEED, 2), (Enchantment.DURABILITY, 4))
        sword(Gold, sulfur.ingot, sulfur.name, sulfur.id, (Enchantment.KNOCKBACK, 2), (Enchantment.DURABILITY, 4))
        armor(Gold, sulfur.ingot, sulfur.name, sulfur.id, (Enchantment.THORNS, 2), (Enchantment.DURABILITY, 4))

        tools(Iron, orichalcum.ingot, orichalcum.name, orichalcum.id, (Enchantment.SILK_TOUCH, 1))
        sword(Iron, orichalcum.ingot, orichalcum.name, orichalcum.id, (Enchantment.LOOT_BONUS_MOBS, 2))
        armor(Iron, orichalcum.ingot, orichalcum.name, orichalcum.id, (Enchantment.PROTECTION_FALL, 2))

        tools(Iron, aluminum.ingot, aluminum.name, aluminum.id, (Enchantment.DURABILITY, 3))
        sword(Iron, aluminum.ingot, aluminum.name, aluminum.id, (Enchantment.DURABILITY, 5))
        armor(Iron, aluminum.ingot, aluminum.name, aluminum.id, (Enchantment.DURABILITY, 5))

        tools(Gold, palladium.ingot, palladium.name, palladium.id, (Enchantment.DURABILITY, 4), (Enchantment.SILK_TOUCH, 1))
        sword(Gold, palladium.ingot, palladium.name, palladium.id, (Enchantment.DURABILITY, 4), (Enchantment.LOOT_BONUS_MOBS, 3))
        armor(Gold, palladium.ingot, palladium.name, palladium.id, (Enchantment.DURABILITY, 4), (Enchantment.PROTECTION_PROJECTILE, 2))

        tools(Iron, hihiirogane.ingot, hihiirogane.name, hihiirogane.id, (Enchantment.DURABILITY, 1), (Enchantment.LOOT_BONUS_BLOCKS, 2))
        sword(Iron, hihiirogane.ingot, hihiirogane.name, hihiirogane.id, (Enchantment.DURABILITY, 1), (Enchantment.DAMAGE_ALL, 1))
        armor(Iron, hihiirogane.ingot, hihiirogane.name, hihiirogane.id, (Enchantment.DURABILITY, 1), (Enchantment.PROTECTION_FIRE, 3))

        tools(Iron, zinc.ingot, zinc.name, zinc.id, (Enchantment.DIG_SPEED, 2))
        sword(Iron, zinc.ingot, zinc.name, zinc.id, (Enchantment.DIG_SPEED, 2))
        armor(Iron, zinc.ingot, zinc.name, zinc.id, (Enchantment.THORNS, 4))

        tools(Gold, magnesium.ingot, magnesium.name, magnesium.id, (Enchantment.DIG_SPEED, 10), (Enchantment.DURABILITY, 2))
        sword(Gold, magnesium.ingot, magnesium.name, magnesium.id, (Enchantment.LOOT_BONUS_MOBS, 1), (Enchantment.DURABILITY, 2))
        armor(Gold, magnesium.ingot, magnesium.name, magnesium.id, (Enchantment.OXYGEN, 1), (Enchantment.DURABILITY, 2))

        tools(Iron, meteorite.ingot, meteorite.name, meteorite.id, (Enchantment.DIG_SPEED, 1))
        sword(Iron, meteorite.ingot, meteorite.name, meteorite.id, (Enchantment.KNOCKBACK, 4))
        armor(Iron, meteorite.ingot, meteorite.name, meteorite.id, (Enchantment.OXYGEN, 1), (Enchantment.WATER_WORKER, 1))
