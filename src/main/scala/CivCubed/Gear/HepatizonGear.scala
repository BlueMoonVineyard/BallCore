// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Gear

import CivCubed.CustomItems.ItemRegistry
import org.bukkit.enchantments.Enchantment
import com.google.common.collect.MultimapBuilder
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier

object HepatizonGear:
    import CivCubed.Alloys.Tier2.*
    import Gear.*
    import ToolSet.*

    def registerItems()(using registry: ItemRegistry): Unit =
        tools(
            Diamond,
            hepatizon.stack,
            hepatizon.name,
            hepatizon.id,
            DiamondToolSetCustomModelDatas.hepatizon,
            (Enchantment.UNBREAKING, 1),
            (Enchantment.EFFICIENCY, 3),
        )
        swordCustom(
            Diamond,
            hepatizon.stack,
            hepatizon.name,
            hepatizon.id,
            DiamondToolSetCustomModelDatas.hepatizon,
            { is =>
                val meta = is.getItemMeta()
                val map = MultimapBuilder
                    .hashKeys()
                    .arrayListValues()
                    .build[Attribute, AttributeModifier]()
                map.put(
                    Attribute.GENERIC_ATTACK_SPEED,
                    AttributeModifier(
                        "hepatizon_sword_speed",
                        0.3d,
                        AttributeModifier.Operation.ADD_NUMBER,
                    ),
                )
                meta.setAttributeModifiers(map)
                val _ = is.setItemMeta(meta)
            },
            (Enchantment.UNBREAKING, 1),
            (Enchantment.SWEEPING_EDGE, 1),
        )
