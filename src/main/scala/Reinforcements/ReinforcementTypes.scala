// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

enum ReinforcementTypes(val hp: Int):
    case Stone extends ReinforcementTypes(50)
    case Deepslate extends ReinforcementTypes(75)
    case CopperLike extends ReinforcementTypes(200)
    case IronLike extends ReinforcementTypes(300)

