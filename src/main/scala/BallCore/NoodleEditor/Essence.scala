package BallCore.NoodleEditor

import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.TextComponents.*
import scala.util.chaining._

object Essence:
    val group = ItemGroup(
        NamespacedKey(
            "ballcore",
            "esassets/minecraft/models/item/stick.jsonsence",
        ),
        ItemStack(Material.ENDER_EYE),
    )
    val template = CustomItemStack.make(
        NamespacedKey("ballcore", "essence"),
        Material.STICK,
        txt"Essence",
        txt"A small fragment of your power",
        txt"Perfect for fueling stuff",
    )
    template.setItemMeta(template.getItemMeta().tap(_.setCustomModelData(16)))

class Essence extends CustomItem:
    override def group: ItemGroup = Essence.group
    override def template: CustomItemStack = Essence.template
