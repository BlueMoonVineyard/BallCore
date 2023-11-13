import org.bukkit.Server
import be.seeseemelk.mockbukkit.MockBukkit
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.BasicItemRegistry
import BallCore.Ores.QuadrantOres
import BallCore.Ores.CardinalOres

class OreSuite extends munit.FunSuite:
    val server = MockBukkit.mock()
    given Server = server

    test("register quadrant ores") {
        given ItemRegistry = BasicItemRegistry()

        QuadrantOres.registerItems()
    }
    test("register cardinal ores") {
        given ItemRegistry = BasicItemRegistry()

        CardinalOres.registerItems()
    }
