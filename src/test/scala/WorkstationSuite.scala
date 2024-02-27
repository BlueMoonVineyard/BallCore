import org.bukkit.NamespacedKey
import org.bukkit.Material

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
