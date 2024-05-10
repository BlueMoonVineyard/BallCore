package CivCubed.Anvils

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.EnchantmentArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.enchantments.Enchantment
import CivCubed.TextComponents.*
import org.bukkit.NamespacedKey
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.PrepareAnvilEvent
import scala.jdk.CollectionConverters.*
import org.bukkit.plugin.Plugin

object NamespacedKey:
    def unapply(n: NamespacedKey): (String, String) =
        (n.getNamespace(), n.getKey())

def cost(of: Enchantment): Int =
    of.getKey() match
        case NamespacedKey("minecraft", "aqua_affinity") => 1
        case NamespacedKey("minecraft", "bane_of_arthropods") => 1
        case NamespacedKey("minecraft", "binding_curse") => 0
        case NamespacedKey("minecraft", "blast_protection") => 1
        case NamespacedKey("minecraft", "breach") => 2
        case NamespacedKey("minecraft", "channeling") => 2
        case NamespacedKey("minecraft", "density") => 2
        case NamespacedKey("minecraft", "depth_strider") => 1
        case NamespacedKey("minecraft", "efficiency") => 2
        case NamespacedKey("minecraft", "feather_falling") => 1
        case NamespacedKey("minecraft", "fire_aspect") => 1
        case NamespacedKey("minecraft", "fire_protection") => 1
        case NamespacedKey("minecraft", "flame") => 2
        case NamespacedKey("minecraft", "fortune") => 2
        case NamespacedKey("minecraft", "frost_walker") => 1
        case NamespacedKey("minecraft", "impaling") => 2
        case NamespacedKey("minecraft", "infinity") => 4
        case NamespacedKey("minecraft", "knockback") => 2
        case NamespacedKey("minecraft", "looting") => 2
        case NamespacedKey("minecraft", "loyalty") => 1
        case NamespacedKey("minecraft", "luck_of_the_sea") => 4
        case NamespacedKey("minecraft", "lure") => 4
        case NamespacedKey("minecraft", "mending") => 8
        case NamespacedKey("minecraft", "multishot") => 2
        case NamespacedKey("minecraft", "piercing") => 2
        case NamespacedKey("minecraft", "power") => 1
        case NamespacedKey("minecraft", "projectile_protection") => 2
        case NamespacedKey("minecraft", "protection") => 4
        case NamespacedKey("minecraft", "punch") => 2
        case NamespacedKey("minecraft", "quick_charge") => 2
        case NamespacedKey("minecraft", "respiration") => 2
        case NamespacedKey("minecraft", "riptide") => 4
        case NamespacedKey("minecraft", "sharpness") => 4
        case NamespacedKey("minecraft", "silk_touch") => 4
        case NamespacedKey("minecraft", "smite") => 2
        case NamespacedKey("minecraft", "soul_speed") => 2
        case NamespacedKey("minecraft", "sweeping_edge") => 4
        case NamespacedKey("minecraft", "swift_sneak") => 2
        case NamespacedKey("minecraft", "thorns") => 4
        case NamespacedKey("minecraft", "unbreaking") => 4
        case NamespacedKey("minecraft", "vanishing_curse") => 0
        case NamespacedKey("minecraft", "wind_burst") => 4

class AnvilHandler extends Listener:
    @EventHandler
    def setAnvilCosts(prepare: PrepareAnvilEvent): Unit =
        val inv = prepare.getInventory
        Option(inv.getResult) match
            case None =>
            case Some(out) =>

                if inv.getSecondItem == null then
                    inv.setRepairCost(1)
                else
                    val oldRes = inv.getFirstItem.getEnchantments.asScala.foldLeft(0) { case (accumulator, (enchantment, level)) =>
                        accumulator + (cost(enchantment) * (level * level)).min(39)
                    }
                    val newRes = out.getEnchantments.asScala.foldLeft(0) { case (accumulator, (enchantment, level)) =>
                        accumulator + (cost(enchantment) * (level * level)).min(39)
                    }

                    if inv.getFirstItem.getEnchantments == inv.getResult.getEnchantments then
                        inv.setRepairCost(newRes / 2)
                    else
                        inv.setRepairCost(newRes - oldRes)


def register()(using p: Plugin): Unit =
    p.getServer.getPluginManager.registerEvents(AnvilHandler(), p)
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
