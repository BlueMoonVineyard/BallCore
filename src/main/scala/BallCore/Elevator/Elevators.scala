package BallCore.Elevator

import org.bukkit.plugin.Plugin

object Elevators:
	def register()(using p: Plugin): Unit =
		p.getServer().getPluginManager().registerEvents(Listener(), p)
