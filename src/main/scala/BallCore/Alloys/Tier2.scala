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

object Tier2:
    val ig = ItemGroup(
        NamespacedKey("ballcore", "alloys_tier_2"),
        ItemStack(Material.ANVIL),
    )

    val ferrobyte = Alloy(
        "Ferrobyte",
        "ferrobye",
        CustomItemStack.make(
            NamespacedKey("ballcore", "ferrobyte_ingot"),
            Material.IRON_INGOT,
            txt"Ferrobyte Ingot",
        ),
    )
    ferrobyte.stack.setItemMeta(
        ferrobyte.stack.getItemMeta().tap(_.setCustomModelData(400))
    )

    val skyBronzeMorning = Alloy(
        "Dawn Bronze",
        "sky_bronze_morning",
        CustomItemStack.make(
            NamespacedKey("ballcore", "sky_bronze_morning_ingot"),
            Material.IRON_INGOT,
            txt"Dawn Bronze Ingot",
        ),
    )
    skyBronzeMorning.stack.setItemMeta(
        skyBronzeMorning.stack.getItemMeta().tap(_.setCustomModelData(410))
    )

    val skyBronzeDay = Alloy(
        "Sky Bronze",
        "sky_bronze_day",
        CustomItemStack.make(
            NamespacedKey("ballcore", "sky_bronze_day_ingot"),
            Material.IRON_INGOT,
            txt"Sky Bronze Ingot",
        ),
    )
    skyBronzeDay.stack.setItemMeta(
        skyBronzeDay.stack.getItemMeta().tap(_.setCustomModelData(411))
    )

    val skyBronzeEvening = Alloy(
        "Dusk Bronze",
        "sky_bronze_evening",
        CustomItemStack.make(
            NamespacedKey("ballcore", "sky_bronze_evening_ingot"),
            Material.IRON_INGOT,
            txt"Dusk Bronze Ingot",
        ),
    )
    skyBronzeEvening.stack.setItemMeta(
        skyBronzeEvening.stack.getItemMeta().tap(_.setCustomModelData(412))
    )

    val skyBronzeNight = Alloy(
        "Star Bronze",
        "sky_bronze_night",
        CustomItemStack.make(
            NamespacedKey("ballcore", "sky_bronze_night_ingot"),
            Material.IRON_INGOT,
            txt"Star Bronze Ingot",
        ),
    )
    skyBronzeNight.stack.setItemMeta(
        skyBronzeNight.stack.getItemMeta().tap(_.setCustomModelData(413))
    )

    val suno = Alloy(
        "Suno",
        "suno",
        CustomItemStack.make(
            NamespacedKey("ballcore", "suno_ingot"),
            Material.IRON_INGOT,
            txt"Suno Ingot",
        ),
    )
    suno.stack.setItemMeta(
        suno.stack.getItemMeta().tap(_.setCustomModelData(420))
    )

    val adamantite = Alloy(
        "Adamantite",
        "adamantite",
        CustomItemStack.make(
            NamespacedKey("ballcore", "adamantite_ingot"),
            Material.IRON_INGOT,
            txt"Adamantite Ingot",
        ),
    )
    adamantite.stack.setItemMeta(
        adamantite.stack.getItemMeta().tap(_.setCustomModelData(430))
    )

    val hepatizon = Alloy(
        "Hepatizon",
        "hepatizon",
        CustomItemStack.make(
            NamespacedKey("ballcore", "hepatizon_ingot"),
            Material.IRON_INGOT,
            txt"Hepatizon Ingot",
        ),
    )
    hepatizon.stack.setItemMeta(
        hepatizon.stack.getItemMeta().tap(_.setCustomModelData(440))
    )

    val manyullyn = Alloy(
        "Manyullyn",
        "manyullyn",
        CustomItemStack.make(
            NamespacedKey("ballcore", "manyullyn_ingot"),
            Material.IRON_INGOT,
            txt"Manyullyn Ingot",
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
