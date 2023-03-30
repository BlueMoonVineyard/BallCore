// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.CustomItem

class Ore(ig: ItemGroup, oreTier: OreTier, vars: OreVariants) extends CustomItem:
    def group = ig
    def template = vars.ore(oreTier)
    val tier = oreTier
    val variants = vars
