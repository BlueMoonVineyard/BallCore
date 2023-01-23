package BallCore.Reinforcements

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon

object Reinforcements:
    def register()(using sf: SlimefunAddon, rm: ReinforcementManager): Unit =
        val plugin = sf.getJavaPlugin()
        plugin.getServer().getPluginManager().registerEvents(Listener(), plugin)
