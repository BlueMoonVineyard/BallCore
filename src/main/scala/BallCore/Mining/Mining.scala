// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Mining

import BallCore.Acclimation
import BallCore.Acclimation.Information
import BallCore.Storage.SQLManager
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

import scala.collection.immutable.Range.Inclusive
import scala.util.Random
import scala.util.chaining.*
import BallCore.Rest.RestManager
import BallCore.CustomItems.CustomItemStack
import BallCore.Advancements.BallAdvancement
import org.bukkit.entity.Player
import BallCore.Mining.Mining.advancements

private class AdvancementTracker(
    matches: List[CustomItemStack],
    advancement: BallAdvancement[_],
    criteria: advancement.Criteria,
):
    def check(player: Player, item: ItemStack): Unit =
        if matches.exists(_.isSimilar(item)) then
            val _ = advancement.grant(player, criteria)

object Mining:
    val stoneBlocks: Set[Material] =
        Set(Material.STONE, Material.DEEPSLATE, Material.TUFF)
    val oceans: Set[Biome] = Set(
        Biome.COLD_OCEAN,
        Biome.DEEP_COLD_OCEAN,
        Biome.DEEP_FROZEN_OCEAN,
        Biome.DEEP_LUKEWARM_OCEAN,
        Biome.DEEP_OCEAN,
        Biome.FROZEN_OCEAN,
        Biome.LUKEWARM_OCEAN,
        Biome.OCEAN,
        Biome.WARM_OCEAN,
    )
    val drops: List[Drops] = List(
        // global ores
        Drops(
            0 to 320,
            2 to 8,
            0.03,
            ItemStack(Material.COAL),
            WorldLocation.everywhere,
        ),
        Drops(
            -64 to 16,
            1 to 4,
            0.031,
            ItemStack(Material.REDSTONE),
            WorldLocation.everywhere,
        ),
    )

    val advancements: List[AdvancementTracker] = List(
    )

    def register()(using
        ac: AntiCheeser,
        as: Acclimation.Storage,
        p: Plugin,
        sql: SQLManager,
        rest: RestManager,
    ): Unit =
        p.getServer.getPluginManager.registerEvents(MiningListener(), p)

case class Drops(
    yLevels: Inclusive,
    amount: Inclusive,
    chance: Double,
    what: ItemStack,
    where: WorldLocation,
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

    import Cardinal.*
    import Quadrant.*

    val norths: List[WorldLocation] =
        List(
            quadrant(northwest),
            quadrant(northeast),
            cardinal(north),
            cardinal(east),
            cardinal(west),
            everywhere,
        )
    val wests: List[WorldLocation] =
        List(
            quadrant(northwest),
            quadrant(southwest),
            cardinal(west),
            cardinal(north),
            cardinal(south),
            everywhere,
        )
    val souths: List[WorldLocation] =
        List(
            quadrant(southwest),
            quadrant(southeast),
            cardinal(south),
            cardinal(east),
            cardinal(west),
            everywhere,
        )
    val easts: List[WorldLocation] =
        List(
            quadrant(northeast),
            quadrant(southeast),
            cardinal(east),
            cardinal(north),
            cardinal(south),
            everywhere,
        )

enum WorldLocation:
    case quadrant(which: Quadrant)
    case cardinal(which: Cardinal)
    case everywhere

class MiningListener()(using
    ac: AntiCheeser,
    as: Acclimation.Storage,
    sql: SQLManager,
    rest: RestManager,
) extends Listener:
    val randomizer: Random = scala.util.Random()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def onBreakBlock(event: BlockBreakEvent): Unit =
        val it1 =
            sql.useBlocking(sql.withR(ac.blockBrokenPartA(event.getBlock)))
        val it2 = ac.blockBrokenPartB(event.getBlock, it1)
        val it3 =
            sql.useBlocking(sql.withR(ac.blockBrokenPartC(event.getBlock, it2)))
        if !it3 then return
        if !Mining.stoneBlocks.contains(event.getBlock.getType) then return
        if Mining.oceans.contains(event.getBlock.getBiome) then return

        val plr = event.getPlayer.getUniqueId
        val (lat, long) =
            Information.latLong(event.getBlock.getX, event.getBlock.getZ)
        val (plat, plong) = (
            sql.useBlocking(sql.withS(as.getLatitude(plr))),
            sql.useBlocking(sql.withS(as.getLongitude(plr))),
        )
        val ezf = Information.exclusionZoneFactor(
            event.getBlock.getX(),
            event.getBlock.getZ(),
        )
        val restFactor =
            if sql.useBlocking(sql.withS(rest.useRest(plr))) then 1.0
            else 0.5

        val dlat = Information.similarityNeg(lat, plat)
        val dlong = Information.similarityNeg(long, plong)

        // multiplier of the bonus on top of baseline rate
        val bonusRateMultiplier = ((dlat + dlong) / 2.0).abs

        // figure out what could possibly drop
        val possibleDrops = Mining.drops
            .filter(_.yLevels.contains(event.getBlock.getY))
            .filter { drops =>
                if event.getBlock.getZ < 0 then
                    WorldLocation.norths.contains(drops.where)
                else WorldLocation.souths.contains(drops.where)
            }
            .filter { drops =>
                if event.getBlock.getX < 0 then
                    WorldLocation.wests.contains(drops.where)
                else WorldLocation.easts.contains(drops.where)
            }

        possibleDrops.find { maybe =>
            val baseline = maybe.chance * 0.2
            val bonus = maybe.chance * 0.8

            val actual =
                (baseline + (bonusRateMultiplier * bonus)) * ezf * restFactor

            if randomizer.nextDouble() <= actual then true
            else false
        } match
            case Some(maybe) =>
                advancements.foreach(_.check(event.getPlayer, maybe.what))
                val dropAmount =
                    maybe.amount(randomizer.nextInt(maybe.amount.length))
                val drop = maybe.what.clone().tap(_.setAmount(dropAmount))
                val _ = event.getBlock.getWorld
                    .dropItemNaturally(event.getBlock.getLocation(), drop)
            case _ =>
                ()
