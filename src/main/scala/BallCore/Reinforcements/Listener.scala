// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Beacons.CivBeaconManager
import BallCore.Groups.{GroupError, GroupManager, Permissions, nullUUID}
import BallCore.Storage.SQLManager
import com.destroystokyo.paper.MaterialTags
import org.bukkit.block.data.`type`.Lectern
import org.bukkit.block.data.{Lightable, Openable}
import org.bukkit.block.{Block, Container, Sign}
import org.bukkit.entity.{EntityType, Player}
import org.bukkit.event.block.*
import org.bukkit.event.entity.{
    EntityChangeBlockEvent,
    EntityExplodeEvent,
    EntitySpawnEvent,
}
import org.bukkit.event.player.*
import org.bukkit.event.world.StructureGrowEvent
import org.bukkit.event.{EventHandler, EventPriority}
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.{Location, Material, Particle}

import scala.jdk.CollectionConverters.*
import scala.util.chaining.*
import BallCore.Fingerprints.FingerprintManager
import BallCore.Fingerprints.FingerprintReason
import cats.effect.IO
import BallCore.TextComponents._
import scala.util.Random
import BallCore.Groups.Position
import BallCore.WebHooks.WebHookManager
import cats.data.OptionT
import BallCore.PrimeTime.{PrimeTimeManager, PrimeTimeResult}
import java.time.temporal.ChronoUnit
import java.time.OffsetDateTime
import BallCore.CustomItems.BlockManager
import BallCore.CustomItems.Listeners
import BallCore.CustomItems.ItemRegistry
import BallCore.Sigils.BattleManager
import cats.effect.kernel.Resource
import skunk.Session
import org.bukkit.block.data.`type`.Switch
import org.bukkit.Tag
import org.bukkit.event.entity.EntityInteractEvent
import BallCore.Groups.GroupID
import BallCore.Groups.SubgroupID
import skunk.Transaction
import BallCore.NoodleEditor.NoodleManager
import BallCore.Beacons.BeaconID
import BallCore.NoodleEditor.DelinquencyManager

object Listener:
    private def centered(at: Location): Location =
        at.clone().tap(_.add(0.5, 0.5, 0.5))

    def playCreationEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone | ReinforcementTypes.Deepslate =>
                (Particle.PORTAL, 200, 0.0, 0.8)
            case ReinforcementTypes.CopperLike =>
                (Particle.NAUTILUS, 100, 0.1, 0.6)
            case ReinforcementTypes.IronLike =>
                (Particle.ENCHANTMENT_TABLE, 50, 0.0, 0.5)
        at.getWorld
            .spawnParticle(
                pType,
                centered(at),
                pCount,
                pOffset,
                pOffset,
                pOffset,
                pSpeed,
                null,
            )

    def playDamageEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone | ReinforcementTypes.Deepslate =>
                (Particle.SMOKE_NORMAL, 40, 0.0, 0.06)
            case ReinforcementTypes.CopperLike =>
                (Particle.BUBBLE_POP, 100, 0.1, 0.2)
            case ReinforcementTypes.IronLike =>
                (Particle.END_ROD, 15, 0.0, 0.045)
        at.getWorld
            .spawnParticle(
                pType,
                centered(at),
                pCount,
                pOffset,
                pOffset,
                pOffset,
                pSpeed,
                null,
            )

    def playBreakEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone | ReinforcementTypes.Deepslate =>
                (Particle.SMOKE_LARGE, 80, 0.0, 0.04)
            case ReinforcementTypes.CopperLike =>
                (Particle.BUBBLE_POP, 500, 0.1, 0.5)
            case ReinforcementTypes.IronLike =>
                (Particle.END_ROD, 200, 0.0, 0.13)
        at.getWorld
            .spawnParticle(
                pType,
                centered(at),
                pCount,
                pOffset,
                pOffset,
                pOffset,
                pSpeed,
                null,
            )

class Listener(using
    cbm: CivBeaconManager,
    gm: GroupManager,
    sql: SQLManager,
    busts: BustThroughTracker,
    fingerprints: FingerprintManager,
    webhooks: WebHookManager,
    primeTime: PrimeTimeManager,
    blockManager: BlockManager,
    ir: ItemRegistry,
    battle: BattleManager,
    noodle: NoodleManager,
    delinquency: DelinquencyManager,
) extends org.bukkit.event.Listener:

    import Listener.*

    //
    //// Stuff that interacts with the RSM; i.e. that mutates block states
    //

    private case class RelevantData(
        group: GroupID,
        subgroup: SubgroupID,
        permissionGranted: Either[GroupError, Boolean],
        primeTime: PrimeTimeResult,
        blockIsHeart: Boolean,
        isCoveredByBattle: Boolean,
        delinquentDays: Int,
    )

    private def getGroupOrBeaconCovering(location: Location)(using
        Session[IO],
        Transaction[IO],
    ): IO[Option[(GroupID, SubgroupID) | BeaconID]] =
        getGroupCovering(location).value.flatMap {
            case x @ Some(_) => IO.pure(x)
            case None => cbm.beaconContaining(location)
        }

    private def getGroupCovering(location: Location)(using
        Session[IO],
        Transaction[IO],
    ): OptionT[IO, (GroupID, SubgroupID)] =
        val position = Position(
            location.getX.toInt,
            location.getY.toInt,
            location.getZ.toInt,
            location.getWorld.getUID,
        )
        val findByBeacon =
            for
                beacon <- OptionT(cbm.beaconContaining(location))
                group <- OptionT(cbm.getGroup(beacon))
                claims <- OptionT.liftF(gm.getSubclaims(group))
                subgroup =
                    claims
                        .getOrElse(Map())
                        .find(_._2.contains(position))
                        .map(_._1)
                        .getOrElse(nullUUID)
            yield (group, subgroup)
        val findByNoodle =
            for noodle <- OptionT(
                    noodle
                        .noodleAt(location)(using
                            Resource.pure[IO, Session[IO]](summon[Session[IO]])
                        )
                )
            yield (noodle.group, noodle.subgroup)
        findByBeacon.orElse(findByNoodle)

    private def getRelevantGroupData(
        player: Player,
        location: Location,
        permission: Permissions,
        breaking: Boolean,
    )(using Session[IO], Transaction[IO]): OptionT[IO, RelevantData] =
        for
            covered <- getGroupCovering(location)
            (group, subgroup) = covered
            permissionGranted <- OptionT.liftF(
                gm.check(player.getUniqueId, group, subgroup, permission).value
            )
            extantBattle <- OptionT.liftF(
                battle.bufferZoneAt(location)(using
                    Resource.pure[IO, Session[IO]](summon[Session[IO]])
                )
            )
            isOk =
                extantBattle.isDefined || permissionGranted == Right(true)
            isInPrimeTime <-
                if breaking && !isOk then
                    OptionT.liftF(primeTime.checkPrimeTime(group))
                else OptionT.pure[IO](PrimeTimeResult.isInPrimeTime)
            heart <- OptionT.liftF(cbm.heartAt(location))
            daysDelinquent <- OptionT.liftF(
                delinquency.daysOfGroupDelinquency(group)
            )
        yield RelevantData(
            group,
            subgroup,
            permissionGranted,
            isInPrimeTime,
            heart.isDefined,
            extantBattle.isDefined,
            daysDelinquent,
        )

    private def doBustThrough(
        l: Location,
        p: Player,
        r: RelevantData,
    )(using Session[IO], Transaction[IO]): IO[(BustResult, Boolean)] =
        IO { busts.bust(l, r.delinquentDays) }.flatMap {
            case BustResult.alreadyBusted =>
                IO.pure(
                    (BustResult.alreadyBusted, true)
                )
            case BustResult.busting =>
                IO.pure(
                    (BustResult.busting, false)
                )
            case BustResult.notBusting =>
                IO.pure(
                    (BustResult.notBusting, false)
                )
            case BustResult.bustingBlocked(x) =>
                IO.pure(
                    (BustResult.bustingBlocked(x), false)
                )
            case BustResult.justBusted =>
                for
                    _ <- fingerprints.storeFingerprintAt(
                        l.getX.toInt,
                        l.getY.toInt,
                        l.getZ.toInt,
                        l.getWorld.getUID,
                        p.getUniqueId,
                        FingerprintReason.bustedThrough,
                    )
                    audience <- gm
                        .groupAudience(r.group)
                        .value
                    coords <- IO {
                        val xOffset =
                            Random.between(-3, 3)
                        val zOffset =
                            Random.between(-3, 3)
                        val x = l.getBlockX() + xOffset
                        val z = l.getBlockZ() + zOffset
                        (x, z)
                    }
                    _ <- IO {
                        audience.foreach((name, aud) =>
                            aud.sendServerMessage(
                                txt"[$name] Someone busted through your beacon approximately around ${coords._1} ± 3 / ${l.getBlockY} / ${coords._2} ± 3"
                            )
                        )
                    }.flatMap { _ =>
                        webhooks.broadcastTo(
                            r.group,
                            s"Someone busted through your beacon approximately around ${coords._1} ± 3 / ${l.getBlockY} / ${coords._2} ± 3",
                        )
                    }
                yield (BustResult.justBusted, true)
        }

    private def decideOn(breaking: Boolean, location: Location, player: Player)(
        r: RelevantData
    )(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[GroupError, (BustResult, Boolean)]] =
        (r.permissionGranted, r.primeTime) match
            case _ if r.isCoveredByBattle && !r.blockIsHeart =>
                IO.pure(Right((BustResult.notBusting, true)))
            case (Right(false), PrimeTimeResult.isInPrimeTime)
                if breaking && !r.blockIsHeart =>
                doBustThrough(location, player, r).map(Right.apply)
            case (Right(false), PrimeTimeResult.notInPrimeTime(reopens))
                if breaking && r.delinquentDays > 7 && !r.blockIsHeart =>
                doBustThrough(location, player, r).map(Right.apply)
            case (Right(false), PrimeTimeResult.notInPrimeTime(reopens))
                if breaking =>
                IO.pure(
                    Right(
                        (BustResult.bustingBlocked(reopens), false)
                    )
                )
            case (Right(false), _) =>
                IO.pure(Right((BustResult.notBusting, false)))
            case (Right(true), _) =>
                IO.pure(Right((BustResult.notBusting, true)))
            case (Left(err), _) =>
                IO.pure(Left(err))

    private def decideOn(breaking: Boolean, location: Location, player: Player)(
        r: Option[RelevantData]
    )(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[GroupError, (BustResult, Boolean)]] =
        r.map(decideOn(breaking, location, player))
            .getOrElse(IO.pure(Right((BustResult.notBusting, true))))

    private def checkAt(
        location: Location,
        player: Player,
        permission: Permissions,
        breaking: Boolean,
    ): Either[GroupError, Boolean] =
        if player.hasPermission("ballcore.bypass") then Right(true)
        else
            sql.useBlocking(sql.withS(sql.withTX(for {
                data <- getRelevantGroupData(
                    player,
                    location,
                    permission,
                    breaking,
                ).value
                ok <- decideOn(breaking, location, player)(data)
            } yield ok)))
                .map { (result, ok) =>
                    result match
                        case BustResult.alreadyBusted =>
                        case BustResult.notBusting =>
                        case BustResult.bustingBlocked(next) =>
                            playBreakEffect(location, ReinforcementTypes.Stone)
                            val time =
                                ChronoUnit.HOURS.between(
                                    OffsetDateTime.now(),
                                    next,
                                )
                            player.sendServerMessage(
                                txt"This group's vulnerability window isn't active."
                            )
                            player.sendServerMessage(
                                txt"It opens in ${time} hours."
                            )
                        case BustResult.busting =>
                            playDamageEffect(
                                location,
                                ReinforcementTypes.IronLike,
                            )
                        case BustResult.justBusted =>
                            playBreakEffect(
                                location,
                                ReinforcementTypes.IronLike,
                            )
                    ok
                }

    private inline def checkAt(
        location: Block,
        player: Player,
        permission: Permissions,
        breaking: Boolean = false,
    ): Either[GroupError, Boolean] =
        checkAt(
            BlockAdjustment.adjustBlock(location).getLocation(),
            player,
            permission,
            breaking,
        )

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        checkAt(
            event.getBlockPlaced,
            event.getPlayer,
            Permissions.Build,
            true,
        ) match
            case Right(ok) if ok =>
                ()
            case _ =>
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onBreak(event: BlockBreakEvent): Unit =
        checkAt(event.getBlock, event.getPlayer, Permissions.Build, true) match
            case Right(ok) if ok =>
                ()
            case _ =>
                event.setCancelled(true)

    //
    //// Stuff that enforces reinforcements in the face of permissions; i.e. chest opening prevention
    //

    // prevent harvesting fingerprints
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    def preventFingerprintHarvesting(event: PlayerInteractEvent): Unit =
        if !event.hasItem || event
                .getItem()
                .getType() != Material.BRUSH || event.getAction != Action.RIGHT_CLICK_BLOCK
        then return ()

        checkAt(event.getClickedBlock, event.getPlayer, Permissions.Build) match
            case Right(ok) if ok =>
                ()
            case _ =>
                event.setCancelled(true)

    // prevent opening reinforced items
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventOpens(event: PlayerInteractEvent): Unit =
        if !event.hasBlock || event.getAction != Action.RIGHT_CLICK_BLOCK then
            return
        val loc = BlockAdjustment.adjustBlock(event.getClickedBlock)
        if loc.getState().isInstanceOf[Container] then
            checkAt(
                event.getClickedBlock,
                event.getPlayer,
                Permissions.Chests,
            ) match
                case Right(ok) if ok =>
                    ()
                case _ =>
                    playDamageEffect(
                        loc.getLocation(),
                        ReinforcementTypes.Stone,
                    )
                    event.setCancelled(true)
        else if loc.getBlockData.isInstanceOf[Openable] then
            checkAt(
                event.getClickedBlock,
                event.getPlayer,
                Permissions.Doors,
            ) match
                case Right(ok) if ok =>
                    ()
                case _ =>
                    playDamageEffect(
                        loc.getLocation(),
                        ReinforcementTypes.Stone,
                    )
                    event.setCancelled(true)
        else if loc.getState().isInstanceOf[Sign] then
            checkAt(
                event.getClickedBlock,
                event.getPlayer,
                Permissions.Signs,
            ) match
                case Right(ok) if ok =>
                    ()
                case _ =>
                    playDamageEffect(
                        loc.getLocation(),
                        ReinforcementTypes.Stone,
                    )
                    event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventHarvestingCaveVines(event: PlayerHarvestBlockEvent): Unit =
        checkAt(
            event.getHarvestedBlock,
            event.getPlayer,
            Permissions.Crops,
        ) match
            case Right(ok) if ok =>
                ()
            case _ =>
                playDamageEffect(
                    event.getHarvestedBlock.getLocation(),
                    ReinforcementTypes.Stone,
                )
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventUsingCustomItems(event: PlayerInteractEvent): Unit =
        if event.getHand != EquipmentSlot.HAND then return
        if !event.hasBlock then return
        if event.getPlayer.isSneaking then return
        if event.getAction != Action.RIGHT_CLICK_BLOCK then return

        sql.useBlocking(
            sql.withS(blockManager.getCustomItem(event.getClickedBlock))
        ) match
            case Some(item: Listeners.BlockClicked) =>
                checkAt(
                    event.getClickedBlock,
                    event.getPlayer,
                    Permissions.Chests,
                ) match
                    case Right(ok) if ok =>
                        ()
                    case _ =>
                        playDamageEffect(
                            event.getClickedBlock.getLocation(),
                            ReinforcementTypes.Stone,
                        )
                        event.setCancelled(true)
            case _ =>

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventWaxingCopper(event: BlockPlaceEvent): Unit =
        if !BlockSets.copperBlocks.contains(event.getBlockPlaced.getType) then
            return

        val player = event.getPlayer
        val loc = event.getBlockPlaced

        checkAt(loc, player, Permissions.Build) match
            case Right(ok) if ok =>
                ()
            case _ =>
                playDamageEffect(loc.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    // prevents grass -> path, grass/dirt -> farmland, logs -> stripped logs, harvesting beehives
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventBlockRightClickChanges(event: PlayerInteractEvent): Unit =
        if !event.hasBlock || event.getAction != Action.RIGHT_CLICK_BLOCK then
            return
        val player = event.getPlayer
        val hand = event.getHand
        if hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND then
            return
        val slot =
            if hand == EquipmentSlot.HAND then
                player.getInventory.getItemInMainHand
            else player.getInventory.getItemInOffHand

        val loc = event.getClickedBlock
        checkAt(loc, player, Permissions.Build) match
            case Right(ok) if ok =>
                return
            case _ =>
                ()

        playDamageEffect(loc.getLocation(), ReinforcementTypes.Stone)
        val btype = loc.getType

        if MaterialTags.SHOVELS.isTagged(slot.getType) then
            // prevent grass -> path
            if btype == Material.GRASS_BLOCK then event.setCancelled(true)
            // prevent extinguishing campfires with shovels
            else if BlockSets.campfires.contains(btype) then
                val lightable = loc.getBlockData.asInstanceOf[Lightable]
                if lightable.isLit then event.setCancelled(true)
        // prevent making farmland
        else if MaterialTags.HOES.isTagged(slot.getType) then
            if BlockSets.farmlandableBlocks.contains(btype) then
                event.setCancelled(true)
        // prevent stripping logs
        else if MaterialTags.AXES.isTagged(slot.getType) then
            if BlockSets.logs.contains(btype) then event.setCancelled(true)
        // prevent harvesting beehives
        else if BlockSets.beehiveHarvestingTools.contains(slot.getType) then
            if BlockSets.beehives.contains(btype) then event.setCancelled(true)
        // prevent modifiying candles
        else if BlockSets.candles.contains(btype) then
            // prevent extinguishing candles
            if !event.hasItem then
                val lightable = loc.getBlockData.asInstanceOf[Lightable]
                if lightable.isLit then event.setCancelled(true)
            // prevent lighting candles
            else if BlockSets.igniters.contains(slot.getType) then
                event.setCancelled(true)
        // prevent lighting campfires
        else if BlockSets.campfires.contains(btype) then
            val lightable = loc.getBlockData.asInstanceOf[Lightable]
            if !lightable.isLit && BlockSets.igniters.contains(slot.getType)
            then event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventModifiyingBeacon(event: PlayerInteractEvent): Unit =
        if !event.hasBlock || event.getAction != Action.RIGHT_CLICK_BLOCK then
            return
        if event.getClickedBlock.getType != Material.BEACON then return

        val player = event.getPlayer
        val loc = event.getClickedBlock

        checkAt(loc, player, Permissions.Build) match
            case Right(ok) if ok =>
            case _ =>
                playDamageEffect(loc.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPuttingBookInLectern(event: PlayerInteractEvent): Unit =
        if event.getAction != Action.RIGHT_CLICK_BLOCK then return
        val block = event.getClickedBlock
        if block.getType != Material.LECTERN then return
        val blockData = block.getBlockData.asInstanceOf[Lectern]
        if blockData.hasBook then return
        val player = event.getPlayer

        checkAt(block, player, Permissions.Build) match
            case Right(ok) if ok =>
            case _ =>
                playDamageEffect(player.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventTakingBookFromLectern(event: PlayerTakeLecternBookEvent): Unit =
        val block = event.getLectern.getBlock
        val player = event.getPlayer

        checkAt(block, player, Permissions.Build) match
            case Right(ok) if ok =>
            case _ =>
                playDamageEffect(block.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    // prevent harvesting reinforced powdered snow w/out perms
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidPickup(event: PlayerBucketFillEvent): Unit =
        if event.getBlockClicked != Material.POWDER_SNOW then return

        val block = event.getBlockClicked
        val player = event.getPlayer

        checkAt(block, player, Permissions.Build) match
            case Right(ok) if ok =>
            case _ =>
                playDamageEffect(block.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    // prevent placing liquids in blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidPlace(event: PlayerBucketEmptyEvent): Unit =
        val block = event.getBlockClicked.getRelative(event.getBlockFace)
        val player = event.getPlayer

        checkAt(block, player, Permissions.Build) match
            case Right(ok) if ok =>
            case _ =>
                playDamageEffect(block.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    // prevent bonemealing blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventBonemealing(event: BlockFertilizeEvent): Unit =
        val player = event.getPlayer

        if player == null then return

        if event.getBlocks.asScala.exists { x =>
                checkAt(x.getBlock, player, Permissions.Crops) match
                    case Right(ok) if ok =>
                        false
                    case _ =>
                        true
            }
        then
            playDamageEffect(player.getLocation(), ReinforcementTypes.Stone)
            event.setCancelled(true)

    // prevent unauthorized switch (button, lever) uses
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventSwitchPresses(event: PlayerInteractEvent): Unit =
        if event.getAction != Action.RIGHT_CLICK_BLOCK then return
        if !event.getClickedBlock.getBlockData.isInstanceOf[Switch] then return

        val block = event.getClickedBlock
        val player = event.getPlayer

        checkAt(block, player, Permissions.Doors) match
            case Right(ok) if ok =>
            case _ =>
                playDamageEffect(block.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    // prevent unauthorized pressure plate uses
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPressurePlateUses(event: PlayerInteractEvent): Unit =
        if event.getAction != Action.PHYSICAL then return
        if !Tag.PRESSURE_PLATES.isTagged(event.getClickedBlock.getType) then
            return

        val block = event.getClickedBlock
        val player = event.getPlayer

        checkAt(block, player, Permissions.Doors) match
            case Right(ok) if ok =>
            case _ =>
                playDamageEffect(block.getLocation(), ReinforcementTypes.Stone)
                event.setCancelled(true)

    //
    //// Stuff that enforces reinforcements in the face of non-permissions; i.e. stuff like preventing water from killing reinforced blocks
    //

    private def locationsAreCoveredByTheSameBeacon(
        l: Location,
        r: Location,
    ): Boolean =
        sql.useBlocking {
            sql.withS(
                sql.withTX(
                    for {
                        lBeacon <- getGroupOrBeaconCovering(l)
                        rBeacon <- getGroupOrBeaconCovering(r)
                    } yield lBeacon == rBeacon
                )
            )
        }

    private def locationIsCoveredBySomething(l: Location): Boolean =
        sql.useBlocking {
            sql.withS(
                sql.withTX(
                    for {
                        lBeacon <- getGroupOrBeaconCovering(l)
                    } yield lBeacon.isDefined
                )
            )
        }

    // prevent pressure plate use by mobs
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPressurePlateUses(event: EntityInteractEvent): Unit =
        if !Tag.PRESSURE_PLATES.isTagged(event.getBlock.getType) then return

        if locationIsCoveredBySomething(event.getBlock.getLocation())
        then event.setCancelled(true)

    // prevent pistons from pushing blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPistonPush(event: BlockPistonExtendEvent): Unit =
        val piston = event.getBlock.getLocation()
        if event.getBlocks.asScala.exists { x =>
                if !locationsAreCoveredByTheSameBeacon(piston, x.getLocation())
                then true
                else false
            }
        then event.setCancelled(true)

    // prevent pistons from pulling blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPistonPull(event: BlockPistonRetractEvent): Unit =
        val piston = event.getBlock.getLocation()
        if event.getBlocks.asScala.exists { x =>
                if !locationsAreCoveredByTheSameBeacon(piston, x.getLocation())
                then true
                else false
            }
        then event.setCancelled(true)

    // prevent fire from burning blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventFire(event: BlockBurnEvent): Unit =
        event.setCancelled(true)

    // prevent zombies from killing doors
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventZombies(event: EntityChangeBlockEvent): Unit =
        if event.getEntity().isInstanceOf[Player] then
            checkAt(
                event.getBlock,
                event.getEntity.asInstanceOf[Player],
                Permissions.Build,
                true,
            ) match
                case Right(ok) if ok =>
                    ()
                case _ =>
                    event.setCancelled(true)
        else if locationIsCoveredBySomething(event.getBlock.getLocation())
        then event.setCancelled(true)

    // prevent reinforced blocks from falling
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventFallingReinforcement(event: EntitySpawnEvent): Unit =
        if event.getEntityType != EntityType.FALLING_BLOCK then return
        if locationIsCoveredBySomething(
                event.getLocation.getBlock.getLocation()
            )
        then
            event.getEntity.setGravity(false)
            event.setCancelled(true)

    // prevent liquids from washing blocks away
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidWashAway(event: BlockFromToEvent): Unit =
        if event.getToBlock.getY < event.getToBlock.getWorld.getMinHeight
        then return
        if !locationsAreCoveredByTheSameBeacon(
                event.getBlock.getLocation,
                event.getToBlock.getLocation,
            )
        then event.setCancelled(true)

    // prevent plants from breaking reinforced blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPlantGrowth(event: StructureGrowEvent): Unit =
        if event.getBlocks.asScala.exists { x =>
                val loc = x.getBlock.getLocation()
                if !locationsAreCoveredByTheSameBeacon(loc, event.getLocation)
                then true
                else false
            }
        then event.setCancelled(true)

    // have explosions damage blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def onExplosion(event: EntityExplodeEvent): Unit =
        val it = event.blockList().iterator()
        while it.hasNext do
            val block = it.next()
            val loc = BlockAdjustment.adjustBlock(block)
            if locationIsCoveredBySomething(loc.getLocation())
            then it.remove()
