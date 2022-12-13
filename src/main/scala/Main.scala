package BallCore

import BallCore.Hearts.Hearts

import org.bukkit.plugin.java.JavaPlugin
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon

final class Main extends JavaPlugin:
    given keyVal: Storage.ConfigKeyVal = new Storage.ConfigKeyVal
    given acclimation: Acclimation.Storage = new Acclimation.Storage
    given ballcore: Main = this
    given addon: SlimefunAddon = new BallCoreSFAddon
    
    override def onEnable() =
        Hearts.registerItems()
    override def onDisable() =
        keyVal.save()
