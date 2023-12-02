import BallCore.CraftingStations.DyeVat
import BallCore.CraftingStations.GlazingKiln
import BallCore.CraftingStations.StationMaker
import BallCore.CraftingStations.Kiln
import BallCore.CraftingStations.ConcreteMixer
import BallCore.CraftingStations.RailManufactory
import BallCore.CraftingStations.Woodcutter
import BallCore.CraftingStations.RedstoneMaker
import BallCore.CraftingStations.CarnivoreKitchen
import BallCore.CraftingStations.HerbivoreKitchen
import BallCore.CraftingStations.CraftingActor
import be.seeseemelk.mockbukkit.MockBukkit
import org.bukkit.plugin.Plugin
import BallCore.UI.Prompts
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
        assert(ExactChoice(ItemStacks.copper.ingot).test(ItemStacks.copper.ingot), "sanity check")
    }
    test("create workstations") {
        given Plugin = MockBukkit.createMockPlugin()
        given CraftingActor = CraftingActor()
        given Prompts = Prompts()

        DyeVat()
        GlazingKiln()
        Kiln()
        StationMaker()
        ConcreteMixer()
        RailManufactory()
        Woodcutter()
        RedstoneMaker()
        CarnivoreKitchen()
        HerbivoreKitchen()
    }
