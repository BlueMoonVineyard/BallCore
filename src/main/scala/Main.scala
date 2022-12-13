package BallCore

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import org.bukkit.entity.Player

final class BallCore extends JavaPlugin {
	given kvs: KeyValStorage = new MemoryKeyValStorage
	given acclimation: PlayerAcclimation = new PlayerAcclimation
	
	override def onEnable() = {
	}
	override def onDisable() = {
	}
}
