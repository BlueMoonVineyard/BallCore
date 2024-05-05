// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Chat

import CivCubed.DataStructures.ShutdownCallbacks
import CivCubed.Groups.GroupManager
import CivCubed.Storage.SQLManager
import org.bukkit.plugin.Plugin
import CivCubed.Sidebar.SidebarActor

object Chat:
    def register()(using
        p: Plugin,
        gm: GroupManager,
        sm: ShutdownCallbacks,
        sql: SQLManager,
        sidebar: SidebarActor,
    ): ChatActor =
        given a: ChatActor = ChatActor()

        a.startListener()
        p.getServer.getPluginManager.registerEvents(ChatListener(), p)
        a
