// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Mining

import org.bukkit.Material
import BallCore.Acclimation
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import BallCore.Acclimation.Information
import scala.collection.immutable.Range.Inclusive
import org.bukkit.inventory.ItemStack
import BallCore.Ores.CardinalOres
import BallCore.Ores.QuadrantOres
import scala.util.chaining._
import org.bukkit.plugin.Plugin
import org.bukkit.block.Biome
import BallCore.Storage.SQLManager

object Mining:
    val stoneBlocks = Set(Material.STONE, Material.DEEPSLATE, Material.TUFF)
    val oceans = Set(
        Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN, Biome.DEEP_FROZEN_OCEAN, Biome.DEEP_LUKEWARM_OCEAN,
        Biome.DEEP_OCEAN, Biome.FROZEN_OCEAN, Biome.LUKEWARM_OCEAN, Biome.OCEAN, Biome.WARM_OCEAN,
    )
    val drops = List(
        // global ores
        Drops(
            0 to 320,
            2 to 8,
            0.005,
            ItemStack(Material.COAL),
            WorldLocation.everywhere,
        ),
        Drops(
            -64 to 16,
            1 to 4,
            0.005125,
            ItemStack(Material.REDSTONE),
            WorldLocation.everywhere,
        ),
    ).concat {
        // black cardinal ores
        val ores = List(
            Cardinal.north -> CardinalOres.ItemStacks.silver.raw,
            Cardinal.south -> CardinalOres.ItemStacks.sillicon.raw,
            Cardinal.east -> CardinalOres.ItemStacks.cobalt.raw,
            Cardinal.west -> CardinalOres.ItemStacks.lead.raw,
        )
        ores.map((cardinal, ore) => Drops(0 to 320, 1 to 4, 0.001, ore, WorldLocation.cardinal(cardinal)))
    }.concat {
        // blue cardinal ores
        val ores = List(
            Cardinal.north -> CardinalOres.ItemStacks.sapphire,
            Cardinal.south -> CardinalOres.ItemStacks.diamond,
            Cardinal.east -> CardinalOres.ItemStacks.plutonium,
            Cardinal.west -> CardinalOres.ItemStacks.emerald,
        )
        ores.map((cardinal, ore) => Drops(-64 to 16, 1 to 3, 0.00027135, ore, WorldLocation.cardinal(cardinal)))
    }.concat {
        // white quadrant ores
        val ores = List(
            Quadrant.southeast -> QuadrantOres.ItemStacks.iron.raw,
            Quadrant.southwest -> QuadrantOres.ItemStacks.tin.raw,
            Quadrant.northwest -> QuadrantOres.ItemStacks.aluminum.raw,
            Quadrant.northeast -> QuadrantOres.ItemStacks.zinc.raw,
        )
        ores.map((quadrant, ore) => Drops(-64 to 320, 1 to 4, 0.0035, ore, WorldLocation.quadrant(quadrant)))
    }.concat {
        // yellow quadrant ores
        val ores = List(
            Quadrant.southeast -> QuadrantOres.ItemStacks.gold.raw,
            Quadrant.southwest -> QuadrantOres.ItemStacks.sulfur.raw,
            Quadrant.northwest -> QuadrantOres.ItemStacks.palladium.raw,
            Quadrant.northeast -> QuadrantOres.ItemStacks.magnesium.raw,
        )
        ores.map((quadrant, ore) => Drops(-64 to 32, 1 to 4, 0.0007185, ore, WorldLocation.quadrant(quadrant)))
    }.concat {
        // red quadrant ores
        val ores = List(
            Quadrant.southeast -> QuadrantOres.ItemStacks.copper.raw,
            Quadrant.southwest -> QuadrantOres.ItemStacks.orichalcum.raw,
            Quadrant.northwest -> QuadrantOres.ItemStacks.hihiirogane.raw,
            Quadrant.northeast -> QuadrantOres.ItemStacks.meteorite.raw,
        )
        ores.map((quadrant, ore) => Drops(-16 to 112, 1 to 5, 0.001, ore, WorldLocation.quadrant(quadrant)))
    }

    def register()(using ac: AntiCheeser, as: Acclimation.Storage, p: Plugin, sql: SQLManager): Unit =
        p.getServer().getPluginManager().registerEvents(MiningListener(), p)

case class Drops(
    val yLevels: Inclusive,
    val amount: Inclusive,
    val chance: Double,
    val what: ItemStack,
    val where: WorldLocation,
)

enum Quadrant:
    case northwest
    case northeast
    case southeast
    case southwest
enum Cardinal:
    case north
    case east
    case south
    case west
object WorldLocation:
    import Quadrant._
    import Cardinal._
    val norths = List(quadrant(northwest), quadrant(northeast), cardinal(north), everywhere)
    val wests = List(quadrant(northwest), quadrant(southwest), cardinal(west), everywhere)
    val souths = List(quadrant(southwest), quadrant(southeast), cardinal(south), everywhere)
    val easts = List(quadrant(northeast), quadrant(southeast), cardinal(east), everywhere)
enum WorldLocation:
    case quadrant(which: Quadrant)
    case cardinal(which: Cardinal)
    case everywhere

class MiningListener()(using ac: AntiCheeser, as: Acclimation.Storage, sql: SQLManager) extends Listener:
    val randomizer = scala.util.Random()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onBreakBlock(event: BlockBreakEvent): Unit =
        if !sql.useBlocking(ac.blockBroken(event.getBlock())) then
            return
        if !Mining.stoneBlocks.contains(event.getBlock().getType()) then
            return
        if Mining.oceans.contains(event.getBlock().getBiome()) then
            return

        val plr = event.getPlayer().getUniqueId()
        val (lat, long) = Information.latLong(event.getBlock().getX(), event.getBlock().getZ())
        val (plat, plong) = (sql.useBlocking(as.getLatitude(plr)), sql.useBlocking(as.getLongitude(plr)))

        val dlat = Information.similarityNeg(lat, plat)
        val dlong = Information.similarityNeg(long, plong)

        // multiplier of the bonus on top of baseline rate
        val bonusRateMultiplier = math.sqrt((dlat*dlat) + (dlong*dlong))

        // figure out what could possibly drop
        val possibleDrops = Mining.drops.filter(_.yLevels.contains(event.getBlock().getY()))
            .filter { drops =>
                if event.getBlock().getZ() < 0 then
                    WorldLocation.norths.contains(drops.where)
                else
                    WorldLocation.souths.contains(drops.where)
            }.filter { drops =>
                if event.getBlock().getX() < 0 then
                    WorldLocation.wests.contains(drops.where)
                else
                    WorldLocation.easts.contains(drops.where)
            }

        possibleDrops.find { maybe =>
            val baseline = maybe.chance * 0.2
            val bonus = maybe.chance * 0.8

            val actual = baseline + (bonusRateMultiplier * bonus)

            if randomizer.nextDouble() <= actual then
                true
            else
                false
        } match
              case Some(maybe) =>
                  val dropAmount = maybe.amount(randomizer.nextInt(maybe.amount.length))
                  val drop = maybe.what.clone().tap(_.setAmount(dropAmount))
                  val _ = event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop)
              case _ =>
                  ()
