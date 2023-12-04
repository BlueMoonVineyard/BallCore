package BallCore.Alloys

import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Material
import BallCore.TextComponents._
import scala.util.chaining._
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.ItemGroup
import org.bukkit.inventory.ItemStack
import BallCore.CustomItems.PlainCustomItem

case class Alloy(
    name: String,
    id: String,
    stack: CustomItemStack,
)

object Tier1:
    val ig = ItemGroup(
        NamespacedKey("ballcore", "alloys_tier_1"),
        ItemStack(Material.ANVIL),
    )

    val pallalumin = Alloy(
        "Pallalumin",
        "pallalumin",
        CustomItemStack.make(
            NamespacedKey("ballcore", "pallalumin_ingot"),
            Material.IRON_INGOT,
            txt"Pallalumin Ingot",
        ),
    )
    pallalumin.stack.setItemMeta(
        pallalumin.stack.getItemMeta().tap(_.setCustomModelData(300))
    )

    val bronze = Alloy(
        "Bronze",
        "bronze",
        CustomItemStack.make(
            NamespacedKey("ballcore", "bronze_ingot"),
            Material.IRON_INGOT,
            txt"Bronze Ingot",
        ),
    )
    bronze.stack.setItemMeta(
        bronze.stack.getItemMeta().tap(_.setCustomModelData(310))
    )

    val magnox = Alloy(
        "Magnox",
        "magnox",
        CustomItemStack.make(
            NamespacedKey("ballcore", "magnox_ingot"),
            Material.IRON_INGOT,
            txt"Magnox Ingot",
        ),
    )
    magnox.stack.setItemMeta(
        magnox.stack.getItemMeta().tap(_.setCustomModelData(320))
    )

    val gildedIron = Alloy(
        "Gilded Iron",
        "gilded_iron",
        CustomItemStack.make(
            NamespacedKey("ballcore", "gilded_iron_ingot"),
            Material.IRON_INGOT,
            txt"Gilded Iron Ingot",
        ),
    )
    gildedIron.stack.setItemMeta(
        gildedIron.stack.getItemMeta().tap(_.setCustomModelData(330))
    )

    def register()(using ir: ItemRegistry): Unit =
        ir.register(PlainCustomItem(ig, pallalumin.stack))
        ir.register(PlainCustomItem(ig, bronze.stack))
        ir.register(PlainCustomItem(ig, magnox.stack))
        ir.register(PlainCustomItem(ig, gildedIron.stack))
