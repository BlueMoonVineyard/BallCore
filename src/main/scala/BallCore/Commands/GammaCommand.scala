package BallCore.Commands

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class GammaCommand():
    val node =
        CommandTree("gamma")
            .withAliases("fullbright")
            .`then`(
                LiteralArgument("on")
                    .executesPlayer({ (sender, args) =>
                        val _ = sender.addPotionEffect(
                            PotionEffect(
                                PotionEffectType.NIGHT_VISION,
                                PotionEffect.INFINITE_DURATION,
                                0,
                                true,
                                false,
                            )
                        )
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("off")
                    .executesPlayer({ (sender, args) =>
                        sender.removePotionEffect(PotionEffectType.NIGHT_VISION)
                    }: PlayerCommandExecutor)
            )
