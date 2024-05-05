package CivCubed.Alloys

import CivCubed.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Material
import CivCubed.TextComponents._
import scala.util.chaining._
import CivCubed.CustomItems.ItemRegistry
import CivCubed.CustomItems.ItemGroup
import org.bukkit.inventory.ItemStack
import CivCubed.CustomItems.PlainCustomItem

case class Alloy(
    name: String,
    id: String,
    stack: CustomItemStack,
)

object Tier1:
    val ig = ItemGroup(
        NamespacedKey("civcubed", "alloys_tier_1"),
        ItemStack(Material.ANVIL),
    )

    val pallalumin = Alloy(
        "Pallalumin",
        "pallalumin",
        CustomItemStack.make(
            NamespacedKey("civcubed", "pallalumin_ingot"),
            Material.IRON_INGOT,
            trans"items.pallalumin.ingot",
        ),
    )
    pallalumin.stack.setItemMeta(
        pallalumin.stack.getItemMeta().tap(_.setCustomModelData(300))
    )

    val bronze = Alloy(
        "Bronze",
        "bronze",
        CustomItemStack.make(
            NamespacedKey("civcubed", "bronze_ingot"),
            Material.IRON_INGOT,
            trans"items.bronze.ingot",
        ),
    )
    bronze.stack.setItemMeta(
        bronze.stack.getItemMeta().tap(_.setCustomModelData(310))
    )

    val magnox = Alloy(
        "Magnox",
        "magnox",
        CustomItemStack.make(
            NamespacedKey("civcubed", "magnox_ingot"),
            Material.IRON_INGOT,
            trans"items.magnox.ingot",
        ),
    )
    magnox.stack.setItemMeta(
        magnox.stack.getItemMeta().tap(_.setCustomModelData(320))
    )

    val gildedIron = Alloy(
        "Gilded Iron",
        "gilded_iron",
        CustomItemStack.make(
            NamespacedKey("civcubed", "gilded_iron_ingot"),
            Material.IRON_INGOT,
            trans"items.gilded-iron.ingot",
        ),
    )
    gildedIron.stack.setItemMeta(
        gildedIron.stack.getItemMeta().tap(_.setCustomModelData(330))
    )

    val all = List(pallalumin, bronze, magnox, gildedIron)

    def register()(using ir: ItemRegistry): Unit =
        ir.register(PlainCustomItem(ig, pallalumin.stack))
        ir.register(PlainCustomItem(ig, bronze.stack))
        ir.register(PlainCustomItem(ig, magnox.stack))
        ir.register(PlainCustomItem(ig, gildedIron.stack))
