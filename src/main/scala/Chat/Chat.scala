package BallCore.Chat

import org.bukkit.plugin.Plugin
import BallCore.Groups.GroupManager

object Chat:
	def register()(using p: Plugin, gm: GroupManager): ChatActor =
		given a: ChatActor = ChatActor()
		a.startListener()
		p.getServer().getPluginManager().registerEvents(ChatListener(), p)
		a
