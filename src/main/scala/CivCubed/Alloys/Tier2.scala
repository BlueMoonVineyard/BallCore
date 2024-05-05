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

object Tier2:
    val ig = ItemGroup(
        NamespacedKey("civcubed", "alloys_tier_2"),
        ItemStack(Material.ANVIL),
    )

    val ferrobyte = Alloy(
        "Ferrobyte",
        "ferrobye",
        CustomItemStack.make(
            NamespacedKey("civcubed", "ferrobyte_ingot"),
            Material.IRON_INGOT,
            trans"items.ferrobyte.ingot",
        ),
    )
    ferrobyte.stack.setItemMeta(
        ferrobyte.stack.getItemMeta().tap(_.setCustomModelData(400))
    )

    val skyBronzeMorning = Alloy(
        "Dawn Bronze",
        "sky_bronze_morning",
        CustomItemStack.make(
            NamespacedKey("civcubed", "sky_bronze_morning_ingot"),
            Material.IRON_INGOT,
            trans"items.dawn-bronze.ingot",
        ),
    )
    skyBronzeMorning.stack.setItemMeta(
        skyBronzeMorning.stack.getItemMeta().tap(_.setCustomModelData(410))
    )

    val skyBronzeDay = Alloy(
        "Sky Bronze",
        "sky_bronze_day",
        CustomItemStack.make(
            NamespacedKey("civcubed", "sky_bronze_day_ingot"),
            Material.IRON_INGOT,
            trans"items.sky-bronze.ingot",
        ),
    )
    skyBronzeDay.stack.setItemMeta(
        skyBronzeDay.stack.getItemMeta().tap(_.setCustomModelData(411))
    )

    val skyBronzeEvening = Alloy(
        "Dusk Bronze",
        "sky_bronze_evening",
        CustomItemStack.make(
            NamespacedKey("civcubed", "sky_bronze_evening_ingot"),
            Material.IRON_INGOT,
            trans"items.dusk-bronze.ingot",
        ),
    )
    skyBronzeEvening.stack.setItemMeta(
        skyBronzeEvening.stack.getItemMeta().tap(_.setCustomModelData(412))
    )

    val skyBronzeNight = Alloy(
        "Star Bronze",
        "sky_bronze_night",
        CustomItemStack.make(
            NamespacedKey("civcubed", "sky_bronze_night_ingot"),
            Material.IRON_INGOT,
            trans"items.star-bronze.ingot",
        ),
    )
    skyBronzeNight.stack.setItemMeta(
        skyBronzeNight.stack.getItemMeta().tap(_.setCustomModelData(413))
    )

    val suno = Alloy(
        "Suno",
        "suno",
        CustomItemStack.make(
            NamespacedKey("civcubed", "suno_ingot"),
            Material.IRON_INGOT,
            trans"items.suno.ingot",
        ),
    )
    suno.stack.setItemMeta(
        suno.stack.getItemMeta().tap(_.setCustomModelData(420))
    )

    val adamantite = Alloy(
        "Adamantite",
        "adamantite",
        CustomItemStack.make(
            NamespacedKey("civcubed", "adamantite_ingot"),
            Material.IRON_INGOT,
            trans"items.adamantite.ingot",
        ),
    )
    adamantite.stack.setItemMeta(
        adamantite.stack.getItemMeta().tap(_.setCustomModelData(430))
    )

    val hepatizon = Alloy(
        "Hepatizon",
        "hepatizon",
        CustomItemStack.make(
            NamespacedKey("civcubed", "hepatizon_ingot"),
            Material.IRON_INGOT,
            trans"items.hepatizon.ingot",
        ),
    )
    hepatizon.stack.setItemMeta(
        hepatizon.stack.getItemMeta().tap(_.setCustomModelData(440))
    )

    val manyullyn = Alloy(
        "Manyullyn",
        "manyullyn",
        CustomItemStack.make(
            NamespacedKey("civcubed", "manyullyn_ingot"),
            Material.IRON_INGOT,
            trans"items.manyullyn.ingot",
        ),
    )
    manyullyn.stack.setItemMeta(
        manyullyn.stack.getItemMeta().tap(_.setCustomModelData(450))
    )

    val skyBronzes =
        List(skyBronzeMorning, skyBronzeDay, skyBronzeEvening, skyBronzeNight)

    val all =
        List(
            ferrobyte,
            skyBronzeMorning,
            skyBronzeDay,
            skyBronzeEvening,
            skyBronzeNight,
            suno,
            adamantite,
            hepatizon,
            manyullyn,
        )

    def register()(using ir: ItemRegistry): Unit =
        all.foreach(x => ir.register(PlainCustomItem(ig, x.stack)))
