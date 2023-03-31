package BallCore.CustomItems

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object BasicItemRegistry:
    val persistenceKeyID = NamespacedKey("ballcore", "basic_item_registry_id")

class BasicItemRegistry extends ItemRegistry:
    var itemMap = Map[NamespacedKey, CustomItem]()
    def register(item: CustomItem): Unit =
        itemMap += item.id -> item
    def lookup(from: ItemStack): Option[CustomItem] =
        val meta = from.getItemMeta()
        if meta == null then
            return None
        val pdc = meta.getPersistentDataContainer()
        Option(pdc.getOrDefault(BasicItemRegistry.persistenceKeyID, PersistentDataType.STRING, null))
            .map(NamespacedKey.fromString)
            .flatMap(itemMap.get)
    def lookup(from: NamespacedKey): Option[CustomItem] =
        itemMap.get(from)
