import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.inventory.RecipeChoice.ExactChoice
import BallCore.Ores.QuadrantOres.ItemStacks

class WorkstationSuite extends munit.FunSuite:
    val server = mockServerSingleton
    server.createMaterialTag(
        NamespacedKey.minecraft("cherry_logs"),
        "blocks",
        Material.CHERRY_LOG,
    )
    server.createMaterialTag(
        NamespacedKey.minecraft("mangrove_logs"),
        "blocks",
        Material.MANGROVE_LOG,
    )
    test("exactchoice") {
        assert(
            ExactChoice(ItemStacks.copper.ingot).test(ItemStacks.copper.ingot),
            "sanity check",
        )
    }
