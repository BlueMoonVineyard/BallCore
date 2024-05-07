package CivCubed.PVPServer

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.entity.EntityType
import org.bukkit.entity.ArmorStand
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.EntityEquipment
import org.bukkit.plugin.Plugin

object ArmorStandDispenser:
    val pdcKey = NamespacedKey("civcubed", "armor_stand_is_dispenser")
    val node =
        CommandTree("armor-stand-make")
            .withRequirement(_.hasPermission("civcubed.cheat"))
            .executesPlayer({ (sender, args) =>
                val loc = sender.getLocation().getBlock().getLocation()
                loc.add(0.5, 0.5, 0.5)
                loc.setDirection(sender.getLocation.getDirection())
                loc.setYaw(Math.rint(loc.getYaw / 45f).toFloat * 45f)

                val wor = loc.getWorld()

                val as = wor.spawnEntity(loc, EntityType.ARMOR_STAND).asInstanceOf[ArmorStand]
                val myEquipment = sender.getEquipment()
                val armorEquipment = as.getEquipment()
                armorEquipment.setArmorContents(myEquipment.getArmorContents())
                armorEquipment.setItemInMainHand(myEquipment.getItemInMainHand())

                val pdc = as.getPersistentDataContainer()
                pdc.set(pdcKey, PersistentDataType.BOOLEAN, true)

                as.setArms(true)
            }: PlayerCommandExecutor)

    def register()(using p: Plugin) =
        node.register()
        p.getServer.getPluginManager.registerEvents(ArmorStandDispenser(), p)

def isNothing(is: ItemStack): Boolean =
    is == null || is.getType().isEmpty()

class ArmorStandDispenser extends Listener:
    @EventHandler
    def interact(pie: PlayerInteractAtEntityEvent): Unit =
        val entity = pie.getRightClicked()
        val isDispenser = entity.getPersistentDataContainer().getOrDefault(ArmorStandDispenser.pdcKey, PersistentDataType.BOOLEAN, false)

        entity match
            case armorStand: ArmorStand if isDispenser =>
                val targetEquipment = pie.getPlayer().getEquipment()
                val armorEquipment = armorStand.getEquipment()

                val slots = List(
                    ((_: EntityEquipment).getBoots, (_: EntityEquipment).setBoots(_: ItemStack)),
                    ((_: EntityEquipment).getLeggings, (_: EntityEquipment).setLeggings(_: ItemStack)),
                    ((_: EntityEquipment).getChestplate, (_: EntityEquipment).setChestplate(_: ItemStack)),
                    ((_: EntityEquipment).getHelmet, (_: EntityEquipment).setHelmet(_: ItemStack)),
                    ((_: EntityEquipment).getItemInMainHand, (_: EntityEquipment).setItemInMainHand(_: ItemStack)),
                    ((_: EntityEquipment).getItemInOffHand, (_: EntityEquipment).setItemInOffHand(_: ItemStack)),
                )
                slots.foreach { (get, set) =>
                    if !isNothing(get(armorEquipment)) then
                        set(targetEquipment, get(armorEquipment))
                }
                pie.setCancelled(true)
            case _ =>
