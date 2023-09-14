// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.PolygonEditor

trait Model[Self, Msg, Action]:
	def update(msg: Msg): (Self, List[Action])
