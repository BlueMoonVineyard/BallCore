// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Ores

import BallCore.CustomItems.{CustomItem, CustomItemStack, ItemGroup}

class Ore(ig: ItemGroup, oreTier: OreTier, vars: OreVariants)
  extends CustomItem:
  def group: ItemGroup = ig

  def template: CustomItemStack = vars.ore(oreTier)

  val tier: OreTier = oreTier
  val variants: OreVariants = vars
