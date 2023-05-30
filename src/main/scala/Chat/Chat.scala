package BallCore.Chat

import org.bukkit.plugin.Plugin
import BallCore.Groups.GroupManager
import BallCore.DataStructures.ShutdownCallbacks

object Chat:
	def register()(using p: Plugin, gm: GroupManager, sm: ShutdownCallbacks): ChatActor =
		given a: ChatActor = ChatActor()
		a.startListener()
		p.getServer().getPluginManager().registerEvents(ChatListener(), p)
		a
