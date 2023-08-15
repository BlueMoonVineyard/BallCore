package BallCore.CraftingStations

import org.bukkit.Material
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import BallCore.UI.Prompts
import BallCore.CustomItems.CustomItemStack
import org.bukkit.NamespacedKey
import BallCore.UI.Elements._

object GlazingKiln:
    val pairs = List(
        (Material.WHITE_DYE, Material.WHITE_TERRACOTTA, "Dye Terracotta White"),
        (Material.ORANGE_DYE, Material.ORANGE_TERRACOTTA, "Dye Terracotta Orange"),
        (Material.MAGENTA_DYE, Material.MAGENTA_TERRACOTTA, "Dye Terracotta Magenta"),
        (Material.LIGHT_BLUE_DYE, Material.LIGHT_BLUE_TERRACOTTA, "Dye Terracotta Light Blue"),
        (Material.YELLOW_DYE, Material.YELLOW_TERRACOTTA, "Dye Terracotta Yellow"),
        (Material.LIME_DYE, Material.LIME_TERRACOTTA, "Dye Terracotta Lime"),
        (Material.PINK_DYE, Material.PINK_TERRACOTTA, "Dye Terracotta Pink"),
        (Material.GRAY_DYE, Material.GRAY_TERRACOTTA, "Dye Terracotta Gray"),
        (Material.LIGHT_GRAY_DYE, Material.LIGHT_GRAY_TERRACOTTA, "Dye Terracotta Light Gray"),
        (Material.CYAN_DYE, Material.CYAN_TERRACOTTA, "Dye Terracotta Cyan"),
        (Material.PURPLE_DYE, Material.PURPLE_TERRACOTTA, "Dye Terracotta Purple"),
        (Material.BLUE_DYE, Material.BLUE_TERRACOTTA, "Dye Terracotta Blue"),
        (Material.BROWN_DYE, Material.BROWN_TERRACOTTA, "Dye Terracotta Brown"),
        (Material.GREEN_DYE, Material.GREEN_TERRACOTTA, "Dye Terracotta Green"),
        (Material.RED_DYE, Material.RED_TERRACOTTA, "Dye Terracotta Red"),
        (Material.BLACK_DYE, Material.BLACK_TERRACOTTA, "Dye Terracotta Black"),

        (Material.WHITE_DYE, Material.WHITE_GLAZED_TERRACOTTA, "Glaze Terracotta White"),
        (Material.ORANGE_DYE, Material.ORANGE_GLAZED_TERRACOTTA, "Glaze Terracotta Orange"),
        (Material.MAGENTA_DYE, Material.MAGENTA_GLAZED_TERRACOTTA, "Glaze Terracotta Magenta"),
        (Material.LIGHT_BLUE_DYE, Material.LIGHT_BLUE_GLAZED_TERRACOTTA, "Glaze Terracotta Light Blue"),
        (Material.YELLOW_DYE, Material.YELLOW_GLAZED_TERRACOTTA, "Glaze Terracotta Yellow"),
        (Material.LIME_DYE, Material.LIME_GLAZED_TERRACOTTA, "Glaze Terracotta Lime"),
        (Material.PINK_DYE, Material.PINK_GLAZED_TERRACOTTA, "Glaze Terracotta Pink"),
        (Material.GRAY_DYE, Material.GRAY_GLAZED_TERRACOTTA, "Glaze Terracotta Gray"),
        (Material.LIGHT_GRAY_DYE, Material.LIGHT_GRAY_GLAZED_TERRACOTTA, "Glaze Terracotta Light Gray"),
        (Material.CYAN_DYE, Material.CYAN_GLAZED_TERRACOTTA, "Glaze Terracotta Cyan"),
        (Material.PURPLE_DYE, Material.PURPLE_GLAZED_TERRACOTTA, "Glaze Terracotta Purple"),
        (Material.BLUE_DYE, Material.BLUE_GLAZED_TERRACOTTA, "Glaze Terracotta Blue"),
        (Material.BROWN_DYE, Material.BROWN_GLAZED_TERRACOTTA, "Glaze Terracotta Brown"),
        (Material.GREEN_DYE, Material.GREEN_GLAZED_TERRACOTTA, "Glaze Terracotta Green"),
        (Material.RED_DYE, Material.RED_GLAZED_TERRACOTTA, "Glaze Terracotta Red"),
        (Material.BLACK_DYE, Material.BLACK_GLAZED_TERRACOTTA, "Glaze Terracotta Black"),
    )
    val recipes = pairs.map { it =>
        val (dye, terracotta, name) = it
        Recipe(name, List((MaterialChoice(dye), 4), (MaterialChoice(Material.TERRACOTTA), 64)), List(ItemStack(terracotta, 64)), 10, 1)
    }
    val template = CustomItemStack.make(NamespacedKey("ballcore", "glazing_kiln"), Material.SMOKER, txt"Glazing Kiln", txt"Dyes and glazes more terracotta with less dyes than normal crafting")

class GlazingKiln()(using act: CraftingActor, p: Plugin, prompts: Prompts) extends CraftingStation(GlazingKiln.recipes):
    def group = CraftingStations.group
    def template = GlazingKiln.template
