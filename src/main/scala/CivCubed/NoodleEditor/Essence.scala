package CivCubed.NoodleEditor

import CivCubed.CustomItems.CustomItem
import CivCubed.CustomItems.ItemGroup
import CivCubed.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import CivCubed.TextComponents.*
import scala.util.chaining._

object Essence:
    val group = ItemGroup(
        NamespacedKey(
            "civcubed",
            "esassets/minecraft/models/item/stick.jsonsence",
        ),
        ItemStack(Material.ENDER_EYE),
    )
    val template = CustomItemStack.make(
        NamespacedKey("civcubed", "essence"),
        Material.STICK,
        txt"Essence",
        txt"A small fragment of your power",
        txt"Perfect for fueling stuff",
    )
    template.setItemMeta(template.getItemMeta().tap(_.setCustomModelData(16)))

class Essence extends CustomItem:
    override def group: ItemGroup = Essence.group
    override def template: CustomItemStack = Essence.template
