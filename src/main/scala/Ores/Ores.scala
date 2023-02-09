// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.attributes.NotConfigurable

class Ore(ig: ItemGroup, oreTier: OreTier, vars: OreVariants)
    extends SlimefunItem(ig, vars.ore(oreTier), RecipeType.NULL, null), NotConfigurable:

    setUseableInWorkbench(true)

    val tier = oreTier
    val variants = vars
