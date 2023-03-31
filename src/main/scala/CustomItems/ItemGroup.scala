package BallCore.CustomItems

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import scala.util.chaining._
import scala.collection.JavaConverters._
import org.bukkit.ChatColor
import org.bukkit.persistence.PersistentDataType

class CustomItemStack(
    val itemID: NamespacedKey,
    val stack: ItemStack
) extends ItemStack(stack):
    val id = itemID
    setItemMeta(
        getItemMeta()
            .tap(_.getPersistentDataContainer().set(BasicItemRegistry.persistenceKeyID, PersistentDataType.STRING, id.toString()))
    )

object CustomItemStack:
    private def translate = ChatColor.translateAlternateColorCodes('&', _)
    def make(itemID: NamespacedKey, stack: Material, name: String, lore: String*): CustomItemStack =
        val is = ItemStack(stack)
        is.setItemMeta(
            is.getItemMeta()
                .tap(_.setDisplayName(translate(name)))
                .tap(_.setLore(lore.map(translate).asJava))
        )
        CustomItemStack(itemID, is)

case class ItemGroup(
    val key: NamespacedKey,
    val gui: ItemStack,
)
