package CivCubed.Anvils

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.EnchantmentArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.enchantments.Enchantment
import CivCubed.TextComponents.*

def register(): Unit =
    CommandTree("dump-enchantment")
        .`then`(
            EnchantmentArgument("which").executesPlayer({ (sender, args) =>
                val enchantment = args.getUnchecked[Enchantment]("which")
                for level <- enchantment.getStartLevel to enchantment.getMaxLevel do
                    sender.sendServerMessage(enchantment.displayName(level))
                    sender.sendServerMessage(enchantment.getMinModifiedCost(level).toComponent)
                    sender.sendServerMessage(enchantment.getMaxModifiedCost(level).toComponent)
            }: PlayerCommandExecutor)
        )
        .register()
