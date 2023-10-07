// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Chat

import org.bukkit.plugin.Plugin
import BallCore.Groups.GroupManager
import BallCore.DataStructures.ShutdownCallbacks
import BallCore.Storage.SQLManager

object Chat:
	def register()(using p: Plugin, gm: GroupManager, sm: ShutdownCallbacks, sql: SQLManager): ChatActor =
		given a: ChatActor = ChatActor()
		a.startListener()
		p.getServer().getPluginManager().registerEvents(ChatListener(), p)
		a
