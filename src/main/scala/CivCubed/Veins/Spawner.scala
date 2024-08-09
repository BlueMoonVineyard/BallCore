package CivCubed.Veins

import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import scala.util.chaining._
import scala.util.Random
import org.bukkit.HeightMap

def getRandomElement[A](seq: List[A], random: Random): A = 
    seq(random.nextInt(seq.length))

enum Clusters(val full: List[Int], val half: List[Int], val quarter: List[Int]):
    case adamantite extends Clusters(
        List(17, 18, 19, 20),
        List(21, 22),
        List(23, 24),
    )
    case arilla extends Clusters(
        List(25, 26, 27, 28),
        List(29, 30),
        List(31, 32),
    )
    case copper extends Clusters(
        List(33, 34, 35, 36),
        List(37, 38),
        List(39, 40),
    )
    case gold extends Clusters(
        List(41, 42, 43, 44),
        List(45, 46),
        List(47, 48),
    )
    case iron extends Clusters(
        List(49, 50, 51, 52),
        List(53, 54),
        List(55, 56),
    )
    case mythril extends Clusters(
        List(57, 58, 59, 60),
        List(61, 62),
        List(63, 64),
    )
    case orichalcum extends Clusters(
        List(65, 66, 67, 68),
        List(69, 70),
        List(71, 72),
    )
    case palladium extends Clusters(
        List(73, 74, 75, 76),
        List(77, 78),
        List(79, 80),
    )

object Spawner:
    val random = new Random
    def spawnVeinsAround(kind: Clusters, location: Location, radius: Int): Unit =
        for
            x <- -radius to radius
            z <- -radius to radius
            if (x*x + z*z) <= radius*radius
        do
            val block = location.getWorld.getHighestBlockAt(location.getBlockX + x, location.getBlockZ + z, HeightMap.WORLD_SURFACE)
            if block != null then
                val location = block.getLocation
                location.add(0.5, 1.5, 0.5)
                val itemDisplay = block.getWorld.spawnEntity(location, EntityType.ITEM_DISPLAY).asInstanceOf[ItemDisplay]
                val stack = ItemStack(Material.STICK)
                stack.setItemMeta(stack.getItemMeta().tap(_.setCustomModelData(kind.full(random.nextInt(kind.full.length)))))
                itemDisplay.setItemStack(stack)
