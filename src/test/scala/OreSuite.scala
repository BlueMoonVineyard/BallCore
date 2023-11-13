import org.bukkit.Server
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.BasicItemRegistry
import BallCore.Ores.QuadrantOres
import BallCore.Ores.CardinalOres

class OreSuite extends munit.FunSuite:
    given Server = mockServerSingleton

    test("register quadrant ores") {
        given ItemRegistry = BasicItemRegistry()

        QuadrantOres.registerItems()
    }
    test("register cardinal ores") {
        given ItemRegistry = BasicItemRegistry()

        CardinalOres.registerItems()
    }
