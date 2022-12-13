package BallCore

import org.bukkit.plugin.java.JavaPlugin

final class BallCore extends JavaPlugin:
	given keyVal: Storage.KeyVal = new Storage.InMemoryKeyVal
	given acclimation: Acclimation.Storage = new Acclimation.Storage
	
	override def onEnable() =
		()
	override def onDisable() =
		()
