package BallCore

import org.bukkit.NamespacedKey
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.CustomItemStack
import org.bukkit.Material
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.ShapelessRecipe
import scala.util.chaining._
import BallCore.TextComponents._
import BallCore.CustomItems.PlainCustomItem
import BallCore.CustomItems.ItemGroup
import org.bukkit.inventory.ItemStack

object CompressedCobblestone:
    val ids = List(
        ("", "", "9"),
        ("Double ", "double_", "81"),
        ("Triple ", "triple_", "729"),
        ("Quadruple ", "quadruple_", "6,561"),
        ("Quintuple ", "quintuple_", "59,049"),
        ("Sextuple ", "sextuple_", "531,441"),
        ("Septuple ", "septuple_", "4,782,969"),
        ("Octuple ", "octuple_", "43,046,721"),
    ).map((name, id, amount) =>
        CustomItemStack.make(
            NamespacedKey("ballcore", id + "compressed_cobblestone"),
            Material.COBBLESTONE,
            txt"${name}Compressed Cobblestone",
            txt"Contains ${amount} blocks of cobblestone",
        )
    )
    val group = ItemGroup(
        NamespacedKey("ballcore", "compressed_cobblestones"),
        ItemStack(Material.COBBLESTONE),
    )

    def register()(using ir: ItemRegistry): Unit =
        ids.map(x => PlainCustomItem(group, x)).foreach(ir.register)

        // initial recipes

        {
            val prevToNext = NamespacedKey(
                "ballcore",
                "cobblestone_to_compressed_cobblestone",
            )
            val prevToNextRezept = ShapedRecipe(prevToNext, ids(0))
            prevToNextRezept.shape(
                "SSS",
                "SSS",
                "SSS",
            )
            prevToNextRezept.setIngredient('S', Material.COBBLESTONE)

            val nextToPrev = NamespacedKey(
                "ballcore",
                "compressed_cobblestone_cobblestone",
            )
            val nextToPrevRezept =
                ShapelessRecipe(nextToPrev, ItemStack(Material.COBBLESTONE, 9))
            nextToPrevRezept.addIngredient(ExactChoice(ids(0)))

            ir.addRecipe(prevToNextRezept)
            ir.addRecipe(nextToPrevRezept)
        }

        // the next recipes

        ids.sliding(2, 1).foreach { (it) =>
            val prevToNext = NamespacedKey(
                "ballcore",
                it(0).id.getKey() + "_to_" + it(1).id.getKey(),
            )
            val prevToNextRezept = ShapedRecipe(prevToNext, it(1))
            prevToNextRezept.shape(
                "SSS",
                "SSS",
                "SSS",
            )
            prevToNextRezept.setIngredient('S', ExactChoice(it(0)))

            val nextToPrev = NamespacedKey(
                "ballcore",
                it(1).id.getKey() + "_to_" + it(0).id.getKey(),
            )
            val nextToPrevRezept =
                ShapelessRecipe(nextToPrev, it(0).clone().tap(_.setAmount(9)))

            nextToPrevRezept.addIngredient(ExactChoice(it(1)))

            ir.addRecipe(prevToNextRezept)
            ir.addRecipe(nextToPrevRezept)
        }
