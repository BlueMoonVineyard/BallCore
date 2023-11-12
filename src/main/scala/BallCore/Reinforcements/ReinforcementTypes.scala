// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

enum ReinforcementTypes(val hp: Int):
  case Stone extends ReinforcementTypes(50)
  case Deepslate extends ReinforcementTypes(75)
  case CopperLike extends ReinforcementTypes(200)
  case IronLike extends ReinforcementTypes(300)

  def displayName(): String =
    this match
      case Stone      => "Stone"
      case Deepslate  => "Deepslate"
      case CopperLike => "Red"
      case IronLike   => "White"

  def into(): String =
    this match
      case Stone      => "stone"
      case Deepslate  => "deepslate"
      case CopperLike => "copperlike"
      case IronLike   => "ironlike"

object ReinforcementTypes:
  def from(s: String): Option[ReinforcementTypes] =
    s match
      case "stone"      => Some(Stone)
      case "deepslate"  => Some(Deepslate)
      case "copperlike" => Some(CopperLike)
      case "ironlike"   => Some(IronLike)
      case _            => None
