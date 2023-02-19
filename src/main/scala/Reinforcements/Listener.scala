// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.event.block.BlockBreakEvent
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import org.bukkit.Location
import org.bukkit.Particle
import scala.util.chaining._
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.world.StructureGrowEvent
import org.bukkit.block.Container
import org.bukkit.block.data.Openable
import BallCore.Groups.GroupManager
import BallCore.Groups.Permissions
import org.bukkit.event.player.PlayerHarvestBlockEvent
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent

class Listener(using rm: ReinforcementManager, gm: GroupManager, holos: HologramManager) extends org.bukkit.event.Listener:
    def reinforcementFromItem(is: ItemStack): Option[ReinforcementTypes] =
        if is == null then return None
        is.getType() match
            case Material.STONE => Some(ReinforcementTypes.Stone)
            case Material.DEEPSLATE => Some(ReinforcementTypes.Deepslate)
            case Material.IRON_INGOT => Some(ReinforcementTypes.IronLike)
            case Material.COPPER_INGOT => Some(ReinforcementTypes.CopperLike)
            case _ => None

    def centered(at: Location): Location =
        at.clone().tap(_.add(0.5, 0.5, 0.5))

    def playCreationEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone |
                 ReinforcementTypes.Deepslate => (Particle.PORTAL, 200, 0.0, 0.8)
            case ReinforcementTypes.CopperLike => (Particle.NAUTILUS, 100, 0.1, 0.6)
            case ReinforcementTypes.IronLike => (Particle.ENCHANTMENT_TABLE, 50, 0.0, 0.5)
        at.getWorld().spawnParticle(pType, centered(at), pCount, pOffset, pOffset, pOffset, pSpeed, null)

    def playDamageEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone |
                 ReinforcementTypes.Deepslate => (Particle.SMOKE_NORMAL, 40, 0.0, 0.06)
            case ReinforcementTypes.CopperLike => (Particle.BUBBLE_POP, 100, 0.1, 0.2)
            case ReinforcementTypes.IronLike => (Particle.END_ROD, 15, 0.0, 0.045)
        at.getWorld().spawnParticle(pType, centered(at), pCount, pOffset, pOffset, pOffset, pSpeed, null)

    def playBreakEffect(at: Location, kind: ReinforcementTypes): Unit =
        val (pType, pCount, pOffset, pSpeed) = kind match
            case ReinforcementTypes.Stone |
                 ReinforcementTypes.Deepslate => (Particle.SMOKE_LARGE, 80, 0.0, 0.04)
            case ReinforcementTypes.CopperLike => (Particle.BUBBLE_POP, 500, 0.1, 0.5)
            case ReinforcementTypes.IronLike => (Particle.END_ROD, 200, 0.0, 0.13)
        at.getWorld().spawnParticle(pType, centered(at), pCount, pOffset, pOffset, pOffset, pSpeed, null)

    //
    //// Stuff that interacts with the RSM; i.e. that mutates block states
    //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing(_) => ()
            case Unreinforcing() => ()
            case ReinforceAsYouGo(gid, item) =>
                val p = event.getPlayer()
                val i = p.getInventory()
                val loc = BlockAdjustment.adjustBlock(event.getBlockPlaced())
                val slot = i.getStorageContents.find { x =>
                    item.getType() == x.getType() && item.getItemMeta().getDisplayName() == x.getItemMeta().getDisplayName()
                }
                slot match
                    case None =>
                        event.getPlayer().sendMessage(s"no items")
                        // TODO: you ran out of items :/
                        event.setCancelled(true)
                    case Some(value) =>
                        val reinforcement = reinforcementFromItem(value)
                        if reinforcement.isEmpty then
                            event.getPlayer().sendMessage(s"${value}")
                            event.setCancelled(true)
                            return
                        rm.reinforce(p.getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID(), reinforcement.get) match
                            case Left(err) =>
                                event.getPlayer().sendMessage(explain(err))
                            case Right(_) =>
                                playCreationEffect(loc.getLocation(), reinforcement.get)
                                value.setAmount(value.getAmount()-1)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onBreak(event: BlockBreakEvent): Unit =
        val block = BlockAdjustment.adjustBlock(event.getBlock())
        rm.break(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID()) match
            case Left(err) =>
                holos.clear(block)
                err match
                    case JustBroken(bs) =>
                        playBreakEffect(block.getLocation(), bs.kind)
                    case _ =>
                        // event.getPlayer().sendMessage(explain(err))
            case Right(value) =>
                playDamageEffect(block.getLocation(), value.kind)
                holos.display(block, event.getPlayer(), List(s"${value.health} / ${value.kind.hp}"))
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onInteract(event: PlayerInteractEvent): Unit =
        RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
            case Neutral() => ()
            case Reinforcing(gid) =>
                if event.getAction() != Action.RIGHT_CLICK_BLOCK then
                    return
                val reinforcement = reinforcementFromItem(event.getItem())
                if reinforcement.isEmpty then
                    // TODO: reject item use
                    return
                val loc = BlockAdjustment.adjustBlock(event.getClickedBlock())
                val wid = loc.getWorld().getUID()
                rm.reinforce(event.getPlayer().getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), wid, reinforcement.get) match
                    case Left(err) =>
                        event.getPlayer().sendMessage(explain(err))
                    case Right(ok) =>
                        playCreationEffect(loc.getLocation(), reinforcement.get)
                        event.getItem().setAmount(event.getItem().getAmount()-1)
                        event.setCancelled(true)
            case Unreinforcing() =>
                if event.getAction() != Action.RIGHT_CLICK_BLOCK then
                    return

                val loc = BlockAdjustment.adjustBlock(event.getClickedBlock())
                val wid = loc.getWorld().getUID()
                rm.unreinforce(event.getPlayer().getUniqueId(), loc.getX(), loc.getY(), loc.getZ(), wid) match
                    case Left(err) =>
                        event.getPlayer().sendMessage(explain(err))
                    case Right(ok) =>
                        event.setCancelled(true)
                        // TODO: return the materials to the player
            case ReinforceAsYouGo(_, _) => ()

    //
    //// Stuff that enforces reinforcements in the face of permissions; i.e. chest opening prevention
    //

    // prevent opening reinforced items
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventOpens(event: PlayerInteractEvent): Unit =
        if !event.hasBlock() || event.getAction() != Action.RIGHT_CLICK_BLOCK then
            return
        val loc = BlockAdjustment.adjustBlock(event.getClickedBlock())
        val rein = rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isEmpty then
            return
        val reinf = rein.get
        event.getClickedBlock().getState() match
            case _: Container =>
                gm.check(event.getPlayer().getUniqueId(), reinf.group, Permissions.Chests) match
                    case Right(ok) if ok =>
                        ()
                    case _ =>
                        // TODO: notify of permission denied
                        event.setCancelled(true)
            case _: Openable =>
                gm.check(event.getPlayer().getUniqueId(), reinf.group, Permissions.Doors) match
                    case Right(ok) if ok =>
                        ()
                    case _ =>
                        // TODO: notify of permission denied
                        event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventStrippingLogs(event: PlayerInteractEvent): Unit =
        ???

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventHarvestingCaveVines(event: PlayerHarvestBlockEvent): Unit =
        val loc = BlockAdjustment.adjustBlock(event.getHarvestedBlock())
        val rein = rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isEmpty then
            return
        gm.check(event.getPlayer().getUniqueId(), rein.get.group, Permissions.Crops) match
            case Right(ok) if ok =>
                ()
            case _ =>
                // TODO: notify of permission denied
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventWaxingCopper(event: BlockPlaceEvent): Unit =
        ???

    // prevents grass -> path and grass/dirt -> farmland
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventTilling(event: PlayerInteractEvent): Unit =
        ???

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventHarvestingBeehive(event: PlayerInteractEvent): Unit =
        ???

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLightingCandles(event: PlayerInteractEvent): Unit =
        ???

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventModifiyingBeacon(event: PlayerInteractEvent): Unit =
        ???

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPuttingBookInLectern(event: PlayerInteractEvent): Unit =
        ???

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventTakingBookFromLectern(event: PlayerInteractEvent): Unit =
        ???

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventSpreadingMoss(event: PlayerInteractEvent): Unit =
        ???

    // prevent harvesting powdered snow w/out perms
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidPickup(event: PlayerBucketFillEvent): Unit =
        ???

    // prevent placing liquids in blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidPlace(event: PlayerBucketEmptyEvent): Unit =
        val block = event.getBlockClicked().getRelative(event.getBlockFace())
        // air isn't reinforceable, and solid blocks can't become waterlogged
        if block.getType() == Material.AIR || block.getType().isSolid() then
            return
        val rein = rm.getReinforcement(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID())


    //
    //// Stuff that enforces reinforcements in the face of non-permissions; i.e. stuff like preventing water from killing reinforced blocks
    //

    // prevent pistons from pushing blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPistonPush(event: BlockPistonExtendEvent): Unit =
        event.getBlocks().forEach { x =>
            val loc = BlockAdjustment.adjustBlock(event.getBlock())
            val rein = rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
            if rein.isDefined then
                event.setCancelled(true)
        }

    // prevent pistons from pulling blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPistonPull(event: BlockPistonRetractEvent): Unit =
        event.getBlocks().forEach { x =>
            val loc = BlockAdjustment.adjustBlock(event.getBlock())
            val rein = rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
            if rein.isDefined then
                event.setCancelled(true)
        }
    
    // prevent fire from burning blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventFire(event: BlockBurnEvent): Unit =
        val loc = BlockAdjustment.adjustBlock(event.getBlock())
        val rein = rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isDefined then
            event.setCancelled(true)

    // prevent zombies from killing doors
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventZombies(event: EntityChangeBlockEvent): Unit =
        val loc = BlockAdjustment.adjustBlock(event.getBlock())
        val rein = rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isDefined then
            event.setCancelled(true)

    // prevent reinforced blocks from falling
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventFallingReinforcement(event: EntitySpawnEvent): Unit =
        if event.getEntityType() != EntityType.FALLING_BLOCK then
            return
        val block = event.getLocation().getBlock()
        val rein = rm.getReinforcement(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID())
        if rein.isEmpty then
            return
        event.getEntity().setGravity(false)
        event.setCancelled(true)

    // prevent liquids from washing blocks away
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidWashAway(event: BlockFromToEvent): Unit =
        if event.getToBlock().getY() < event.getToBlock().getWorld().getMinHeight() then
            return
        val loc = BlockAdjustment.adjustBlock(event.getBlock())
        val rein = rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isDefined then
            event.setCancelled(true)

    // prevent plants from breaking reinforced blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPlantGrowth(event: StructureGrowEvent): Unit =
        event.getBlocks().forEach { x =>
            val loc = x.getBlock()
            if rm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID()).isDefined then
                event.setCancelled(true)
        }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventSpreadingMossWithDispenser(event: BlockDispenseEvent): Unit =
        ???

    // have explosions damage blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def onExplosion(event: EntityExplodeEvent): Unit =
        ???