package CivCubed.Rest

import org.bukkit.Material
import org.bukkit.Tag
import com.destroystokyo.paper.MaterialTags
import java.util.BitSet
import org.bukkit.Location
import scala.collection.mutable.Queue

enum RoomRole:
    case mining
    case animals
    case crops
    case industrialist
    case enchantments
    case travel
    case builder
    case soldier

enum Evaluation extends Ordered[Evaluation]:
    case luxurious
    case good
    case modest
    case nothing

    override def compare(that: Evaluation): Int =
        that.ordinal - this.ordinal

def count(inner: ((Boolean) => Boolean) => Boolean): Int =
    var count: Int = 0
    inner { cond =>
        if cond then
            count = count + 1
        cond
    }
    count

case class Bounds(
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
)

case class EvaluationResult(
    workstations: Evaluation,
    blocks: Evaluation,
    floor: Evaluation,
    ceiling: Evaluation,
    spacious: Evaluation,
):
    lazy val result = List(workstations, blocks, floor, ceiling, spacious).min

enum EvaluationFailure:
    case notEnclosed
    case noBeds

object RoomEvaluator:
    def evaluateRoomWorkstations(role: RoomRole, workstations: Map[Material, Int]): Evaluation =
        role match
            case RoomRole.mining =>
                count { rule =>
                    val hasStonecutter = rule { workstations.contains(Material.STONECUTTER) }
                    rule { hasStonecutter &&  workstations.getOrElse(Material.FURNACE, 0) + workstations.getOrElse(Material.BLAST_FURNACE, 0) > 3 }
                    rule { workstations.contains(Material.CRAFTING_TABLE) || workstations.contains(Material.CHEST) }
                } match
                    case 3 => Evaluation.luxurious
                    case 2 => Evaluation.good
                    case 1 => Evaluation.modest
                    case _ => Evaluation.nothing
            case RoomRole.animals =>
                count { rule =>
                    rule { workstations.contains(Material.SMOKER) }
                    rule { workstations.contains(Material.CAULDRON) }
                    rule { workstations.contains(Material.LOOM) }
                } match
                    case 3 => Evaluation.luxurious
                    case 2 => Evaluation.good
                    case 1 => Evaluation.modest
                    case _ => Evaluation.nothing
            case RoomRole.crops =>
                count { rule =>
                    rule { workstations.contains(Material.COMPOSTER) }
                    rule { workstations.contains(Material.BARREL) }
                    rule { workstations.contains(Material.LOOM) }
                } match
                    case 2 => Evaluation.luxurious
                    case 1 => Evaluation.modest
                    case _ => Evaluation.nothing
            case RoomRole.industrialist =>
                count { rule =>
                    rule { workstations.contains(Material.BLAST_FURNACE) }
                    rule { workstations.contains(Material.GRINDSTONE) }
                    rule { workstations.contains(Material.FLETCHING_TABLE) }
                    rule { workstations.contains(Material.SMITHING_TABLE) }
                } match
                    case 4 => Evaluation.luxurious
                    case 2 => Evaluation.good
                    case 1 => Evaluation.modest
                    case _ => Evaluation.nothing
            case RoomRole.enchantments =>
                count { rule =>
                    val hasEnchanting = rule { workstations.contains(Material.ENCHANTING_TABLE) }
                    rule { hasEnchanting && workstations.contains(Material.LECTERN) }
                    rule { hasEnchanting && workstations.contains(Material.BREWING_STAND) }
                } match
                    case 3 => Evaluation.luxurious
                    case 2 => Evaluation.good
                    case 1 => Evaluation.modest
                    case _ => Evaluation.nothing
            case RoomRole.travel =>
                count { rule =>
                    val hasCartography = rule { workstations.contains(Material.CARTOGRAPHY_TABLE) }
                    rule { hasCartography && workstations.exists((it, _) => Tag.RAILS.isTagged(it)) }
                } match
                    case 2 => Evaluation.luxurious
                    case 1 => Evaluation.modest
                    case _ => Evaluation.nothing
            case RoomRole.builder =>
                count { rule =>
                    val hasScaffolding = rule { workstations.contains(Material.SCAFFOLDING) }
                    rule { hasScaffolding && workstations.contains(Material.FURNACE) }
                } match
                    case 2 => Evaluation.luxurious
                    case 1 => Evaluation.modest
                    case _ => Evaluation.nothing
            case RoomRole.soldier =>
                Evaluation.luxurious
    def evaluateSpaciousness(role: RoomRole, airBlocks: Int, beds: Int): Evaluation =
        role match
            case RoomRole.soldier =>
                val ratio = (airBlocks / beds)
                if ratio <= 15 then
                    Evaluation.luxurious
                else if ratio <= 40 then
                    Evaluation.good
                else if ratio <= 80 then
                    Evaluation.modest
                else
                    Evaluation.nothing
            case _ =>
                val ratio = (airBlocks / beds)

                val modestRatio = ratio >= 30
                val goodRatio = ratio >= 100
                val luxuriousRatio = ratio >= 200

                if luxuriousRatio then
                    Evaluation.luxurious
                else if goodRatio then
                    Evaluation.good
                else if modestRatio then
                    Evaluation.modest
                else
                    Evaluation.nothing
    def evaluateFloorspace(role: RoomRole, width: Int, length: Int): Evaluation =
        role match
            case RoomRole.soldier =>
                Evaluation.luxurious
            case _ =>
                val modestFloorspace = width * length >= 4*3
                val goodFloorspace = width * length >= 6*5
                val luxuriousFloorspace = width * length >= 6*5

                if luxuriousFloorspace then
                    Evaluation.luxurious
                else if goodFloorspace then
                    Evaluation.good
                else if modestFloorspace then
                    Evaluation.modest
                else
                    Evaluation.nothing
    def evaluateCeiling(role: RoomRole, height: Int): Evaluation =
        role match
            case RoomRole.soldier =>
                Evaluation.luxurious
            case _ =>
                val modestCeiling = height >= 3
                val goodCeiling = height >= 4
                val luxuriousCeiling = height >= 5

                if luxuriousCeiling then
                    Evaluation.luxurious
                else if goodCeiling then
                    Evaluation.good
                else if modestCeiling then
                    Evaluation.modest
                else
                    Evaluation.nothing
    def evaluateRoomBlocks(role: RoomRole, blocks: Map[Material, Int]): Evaluation =
        role match
            case RoomRole.soldier =>
                val blockCount = blocks.foldLeft(0) { case (accumulator, (block, count)) => accumulator + count }
                count { rule =>
                    // obsidian majority
                    rule { blocks.getOrElse(Material.OBSIDIAN, 0) >= blockCount/2 }
                } match
                    case 1 =>
                        Evaluation.luxurious
                    case _ =>
                        Evaluation.nothing
            case _ =>
                count { rule =>
                    // detailing
                    rule { blocks.keySet.exists(it => Tag.STAIRS.isTagged(it) || Tag.SLABS.isTagged(it) || Tag.WOOL_CARPETS.isTagged(it)) }
                    // windows
                    rule { blocks.keySet.exists(it => Tag.FENCES.isTagged(it) || Tag.FENCE_GATES.isTagged(it) || MaterialTags.GLASS.isTagged(it)) }
                    // doors
                    rule { blocks.keySet.exists(it => Tag.DOORS.isTagged(it) && it != Material.IRON_DOOR) }
                } match
                    case 3 =>
                        Evaluation.luxurious
                    case 2 =>
                        Evaluation.good
                    case 1 =>
                        Evaluation.modest
                    case _ =>
                        Evaluation.nothing
    def gatherBlocksAround(location: Location): Option[(Map[Material, Int], Bounds)] =
        val scanLimit: Byte = 13
        val bitset = Array.ofDim[BitSet](scanLimit, scanLimit).mapInPlace(_.mapInPlace(_ => BitSet(scanLimit)))

        var maxX = Int.MinValue
        var maxY = Int.MinValue
        var maxZ = Int.MinValue
        var minX = Int.MaxValue
        var minY = Int.MaxValue
        var minZ = Int.MaxValue

        // location is (center, center, center)
        val center: Byte = (scanLimit / 2).toByte

        val queue = Queue((center, center, center))

        val originX = location.getBlockX
        val originY = location.getBlockY
        val originZ = location.getBlockZ

        val items = scala.collection.mutable.Map[Material, Int]()
        var enclosed = true

        while !queue.isEmpty do
            val (x, y, z) = queue.dequeue

            val worldX = originX + (x - center)
            val worldY = originY + (y - center)
            val worldZ = originZ + (z - center)

            maxX = maxX.max(worldX)
            maxY = maxY.max(worldY)
            maxZ = maxZ.max(worldZ)
            minX = minX.min(worldX)
            minY = minY.min(worldY)
            minZ = minZ.min(worldZ)

            val block = location.getWorld.getBlockAt(worldX, worldY, worldZ)

            items(block.getType) = items.getOrElseUpdate(block.getType, 0) + 1

            if block.isPassable then
                if x < scanLimit-1 && !bitset((x + 1).toByte)((y + 0).toByte).get((z + 0).toByte) then
                    bitset((x + 1).toByte)((y + 0).toByte).flip((z + 0).toByte)
                    queue.enqueue(((x + 1).toByte, (y + 0).toByte, (z + 0).toByte))
                if x > 0 && !bitset((x - 1).toByte)((y + 0).toByte).get((z + 0).toByte) then
                    bitset((x - 1).toByte)((y + 0).toByte).flip((z + 0).toByte)
                    queue.enqueue(((x - 1).toByte, (y + 0).toByte, (z + 0).toByte))
                if y < scanLimit-1 && !bitset((x + 0).toByte)((y + 1).toByte).get((z + 0).toByte) then
                    bitset((x + 0).toByte)((y + 1).toByte).flip((z + 0).toByte)
                    queue.enqueue(((x + 0).toByte, (y + 1).toByte, (z + 0).toByte))
                if y > 0 && !bitset((x + 0).toByte)((y - 1).toByte).get((z + 0).toByte) then
                    bitset((x + 0).toByte)((y - 1).toByte).flip((z + 0).toByte)
                    queue.enqueue(((x + 0).toByte, (y - 1).toByte, (z + 0).toByte))
                if z < scanLimit-1 && !bitset((x + 0).toByte)((y + 0).toByte).get((z + 1).toByte) then
                    bitset((x + 0).toByte)((y + 0).toByte).flip((z + 1).toByte)
                    queue.enqueue(((x + 0).toByte, (y + 0).toByte, (z + 1).toByte))
                if z > 0 && !bitset((x + 0).toByte)((y + 0).toByte).get((z - 1).toByte) then
                    bitset((x + 0).toByte)((y + 0).toByte).flip((z - 1).toByte)
                    queue.enqueue(((x + 0).toByte, (y + 0).toByte, (z - 1).toByte))
                if !(x < scanLimit-1) || !(x > 0) || !(y < scanLimit-1) || !(y > 0) || !(z < scanLimit-1) || !(z > 0) then
                    enclosed = false
                    queue.clear()

        if enclosed then
            Some((items.toMap, Bounds(maxX, maxY, maxZ, minX, minY, minZ)))
        else
            None

    def evaluateRoomForRoleAndBlocks(role: RoomRole, blocks: Map[Material, Int], bounds: Bounds): EvaluationResult =
        val bedBlocks = blocks.filter((key, _) => Tag.BEDS.isTagged(key)).foldLeft(0) { case (accumulator, (_, count)) =>
            accumulator + count
        } / 2
        val airBlocks = blocks.filter((key, _) => key.isAir()).foldLeft(0) { case (accumulator, (_, count)) =>
            accumulator + count
        }
        val workstations = evaluateRoomWorkstations(role, blocks)
        val ceiling = evaluateCeiling(role, bounds.maxY - bounds.minY)
        val floor = evaluateFloorspace(role, bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ)
        val spaciousness = evaluateSpaciousness(role, airBlocks, bedBlocks)
        val block = evaluateRoomBlocks(role, blocks)
        EvaluationResult(workstations, block, floor, ceiling, spaciousness)

    def evaluateRoomForRole(role: RoomRole, location: Location): Either[EvaluationFailure, EvaluationResult] =
        val blocks = gatherBlocksAround(location)
        blocks match
            case None =>
                Left(EvaluationFailure.notEnclosed)
            case Some((blocks, bounds)) =>
                val bedBlocks = blocks.filter((key, _) => Tag.BEDS.isTagged(key)).foldLeft(0) { case (accumulator, (_, count)) =>
                    accumulator + count
                } / 2
                if bedBlocks == 0 then
                    Left(EvaluationFailure.noBeds)
                else
                    Right(evaluateRoomForRoleAndBlocks(role, blocks, bounds))

    def getMajorityRole(location: Location): Either[EvaluationFailure, Array[(RoomRole, Evaluation)]] =
        val blocks = gatherBlocksAround(location)
        blocks match
            case None =>
                Left(EvaluationFailure.notEnclosed)
            case Some((blocks, bounds)) =>
                val bedBlocks = blocks.filter((key, _) => Tag.BEDS.isTagged(key)).foldLeft(0) { case (accumulator, (_, count)) =>
                    accumulator + count
                } / 2
                if bedBlocks == 0 then
                    Left(EvaluationFailure.noBeds)
                else
                    val evaluations = RoomRole.values
                        .map(role => (role, evaluateRoomForRoleAndBlocks(role, blocks, bounds).result))
                        .sortBy(_._2)
                    Right(evaluations)

