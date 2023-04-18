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
import org.bukkit.event.block.BlockFertilizeEvent
import com.destroystokyo.paper.MaterialTags
import org.bukkit.block.data.Lightable
import org.bukkit.event.inventory.InventoryInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.ClickType
import BallCore.UI.Prompts
import org.bukkit.entity.Player
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.block.data.`type`.Lectern
import org.bukkit.event.player.PlayerTakeLecternBookEvent
import BallCore.CustomItems.ItemRegistry
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import org.bukkit.plugin.Plugin
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

object Listener:
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

class Listener(using brm: BlockReinforcementManager, registry: ItemRegistry, gm: GroupManager, holos: HologramManager, prompts: Prompts, plugin: Plugin) extends org.bukkit.event.Listener:
    import Listener._

    def reinforcementFromItem(is: ItemStack): Option[ReinforcementTypes] =
        if is == null then return None
        is.getType() match
            case Material.STONE => Some(ReinforcementTypes.Stone)
            case Material.DEEPSLATE => Some(ReinforcementTypes.Deepslate)
            case Material.IRON_INGOT => Some(ReinforcementTypes.IronLike)
            case Material.COPPER_INGOT => Some(ReinforcementTypes.CopperLike)
            case _ => None

    //
    //// Plumb-and-square crafting and group switching
    //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onShiftLeftClick(event: InventoryClickEvent): Unit =
        if event.getClick() != ClickType.SHIFT_LEFT then
            return
        val h = event.getWhoClicked()
        if !h.isInstanceOf[Player] then
            return
        val p = h.asInstanceOf[Player]
        val istack = event.getCurrentItem()
        val item = registry.lookup(istack)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then
            return

        event.setCancelled(true)
        p.closeInventory()
        given ctx: ExecutionContext = EntityExecutionContext(p)
        prompts.prompt(p, "What group do you want to reinforce on?").map { group =>
            gm.userGroups(p.getUniqueId()).map(_.find(_.name.toLowerCase().contains(group.toLowerCase()))) match
                case Left(err) =>
                    p.sendMessage(err.explain())
                case Right(Some(group)) =>
                    RuntimeStateManager.states(p.getUniqueId()) = group.id
                case Right(None) =>
                    p.sendMessage(s"I couldn't find a group matching '${group}'")
        }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onPrepareCraft(event: PrepareItemCraftEvent): Unit =
        val inv = event.getInventory()
        val recp = inv.getRecipe()
        if recp == null then
            return

        val res = recp.getResult().clone()
        val item = registry.lookup(res)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then
            return

        val h = event.getView().getPlayer()
        if !h.isInstanceOf[Player] then
            return

        val p = h.asInstanceOf[Player]
        val pas = item.get.asInstanceOf[PlumbAndSquare]
        val pasStack = inv.getItem(inv.first(PlumbAndSquare.itemStack.getType()))
        val existingMats = pas.getMaterials(pasStack)
        val craftingWith = inv.getMatrix().filterNot(_ == null).filterNot(_.getType() == PlumbAndSquare.itemStack.getType())(0)

        if !existingMats.isEmpty then
            val (kind, count) = existingMats.get
            if Some(kind) != reinforcementFromItem(craftingWith) then
                inv.setResult(ItemStack(Material.AIR))
                return

        val kind = reinforcementFromItem(craftingWith).get
        val newStack = pasStack.clone()
        newStack.setAmount(1)
        pas.loadReinforcementMaterials(p, newStack, craftingWith.getAmount(), kind)

        inv.setResult(newStack)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onDoCraft(event: CraftItemEvent): Unit =
        val inv = event.getInventory()
        val res = inv.getResult()

        if res == null then
            return

        val item = registry.lookup(res)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then
            return

        val craftingWith = inv.getMatrix().filterNot(_ == null).filterNot(_.getType() == PlumbAndSquare.itemStack.getType())(0)
        craftingWith.setAmount(0)

    //
    //// Stuff that interacts with the RSM; i.e. that mutates block states
    //

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onBlockPlace(event: BlockPlaceEvent): Unit =
        val p = event.getPlayer()
        val i = p.getInventory()
        val istack = i.getItemInOffHand()
        val item = registry.lookup(istack)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then
            return
        if !RuntimeStateManager.states.contains(p.getUniqueId()) then
            p.sendMessage("Shift left-click the plumb-and-square in your inventory to set a group to reinforce on before reinforcing")
            event.setCancelled(true)
            return

        val pas = item.get.asInstanceOf[PlumbAndSquare]
        val mats = pas.getMaterials(istack)
        if mats.isEmpty then
            return
        val (kind, amount) = mats.get
        if amount < 1 then
            return
        val loc = BlockAdjustment.adjustBlock(event.getBlockPlaced())

        val gid = RuntimeStateManager.states(p.getUniqueId())
        brm.reinforce(p.getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID(), kind) match
            case Left(err) =>
                event.getPlayer().sendMessage(explain(err))
            case Right(_) =>
                playCreationEffect(loc.getLocation(), kind)
                pas.loadReinforcementMaterials(p, istack, -1, kind)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    def onBreak(event: BlockBreakEvent): Unit =
        val block = BlockAdjustment.adjustBlock(event.getBlock())
        brm.break(block.getX(), block.getY(), block.getZ(), block.getType().getHardness(), block.getWorld().getUID()) match
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
        val p = event.getPlayer()
        val i = p.getInventory()
        val istack = i.getItemInMainHand()
        val item = registry.lookup(istack)
        if !item.isDefined || !item.get.isInstanceOf[PlumbAndSquare] then
            return
        if !RuntimeStateManager.states.contains(p.getUniqueId()) then
            p.sendMessage("Shift left-click the plumb-and-square in your inventory to set a group to reinforce on before reinforcing")
            event.setCancelled(true)
            return

        val pas = item.get.asInstanceOf[PlumbAndSquare]
        val mats = pas.getMaterials(istack)
        if mats.isEmpty then
            return
        val (kind, amount) = mats.get
        if amount < 1 then
            return
        val loc = BlockAdjustment.adjustBlock(event.getClickedBlock())

        val gid = RuntimeStateManager.states(p.getUniqueId())
        brm.reinforce(p.getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID(), kind) match
            case Left(err) =>
                event.getPlayer().sendMessage(explain(err))
            case Right(_) =>
                playCreationEffect(loc.getLocation(), kind)
                pas.loadReinforcementMaterials(p, istack, -1, kind)
        // RuntimeStateManager.states(event.getPlayer().getUniqueId()) match
        //     case Neutral() => ()
        //     case Reinforcing(gid) =>
        //         if event.getAction() != Action.RIGHT_CLICK_BLOCK then
        //             return
        //         val reinforcement = reinforcementFromItem(event.getItem())
        //         if reinforcement.isEmpty then
        //             // TODO: reject item use
        //             return
        //         val loc = BlockAdjustment.adjustBlock(event.getClickedBlock())
        //         val wid = loc.getWorld().getUID()
        //         rm.reinforce(event.getPlayer().getUniqueId(), gid, loc.getX(), loc.getY(), loc.getZ(), wid, reinforcement.get) match
        //             case Left(err) =>
        //                 event.getPlayer().sendMessage(explain(err))
        //             case Right(ok) =>
        //                 playCreationEffect(loc.getLocation(), reinforcement.get)
        //                 event.getItem().setAmount(event.getItem().getAmount()-1)
        //                 event.setCancelled(true)
        //     case Unreinforcing() =>
        //         if event.getAction() != Action.RIGHT_CLICK_BLOCK then
        //             return

        //         val loc = BlockAdjustment.adjustBlock(event.getClickedBlock())
        //         val wid = loc.getWorld().getUID()
        //         rm.unreinforce(event.getPlayer().getUniqueId(), loc.getX(), loc.getY(), loc.getZ(), wid) match
        //             case Left(err) =>
        //                 event.getPlayer().sendMessage(explain(err))
        //             case Right(ok) =>
        //                 event.setCancelled(true)
        //                 // TODO: return the materials to the player
        //     case ReinforceAsYouGo(_, _) => ()

    //
    //// Stuff that enforces reinforcements in the face of permissions; i.e. chest opening prevention
    //

    // prevent opening reinforced items
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventOpens(event: PlayerInteractEvent): Unit =
        if !event.hasBlock() || event.getAction() != Action.RIGHT_CLICK_BLOCK then
            return
        val loc = BlockAdjustment.adjustBlock(event.getClickedBlock())
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
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
            case _ =>
                ()

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventHarvestingCaveVines(event: PlayerHarvestBlockEvent): Unit =
        val loc = BlockAdjustment.adjustBlock(event.getHarvestedBlock())
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
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
        if !BlockSets.copperBlocks.contains(event.getBlockPlaced().getType()) then
            return

        val player = event.getPlayer()
        val loc = event.getBlockPlaced()
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isDefined then
            gm.check(player.getUniqueId(), rein.get.group, Permissions.Build) match
                case Right(ok) if ok =>
                    ()
                case _ =>
                    // TODO: notify of permission denied
                    event.setCancelled(true)

    // prevents grass -> path, grass/dirt -> farmland, logs -> stripped logs, harvesting beehives
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventBlockRightClickChanges(event: PlayerInteractEvent): Unit =
        if !event.hasBlock() || event.getAction() != Action.RIGHT_CLICK_BLOCK then
            return
        val player = event.getPlayer()
        val hand = event.getHand()
        if hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND then
            return
        val slot =
            if hand == EquipmentSlot.HAND then
                player.getInventory().getItemInMainHand()
            else
                player.getInventory().getItemInOffHand()

        val loc = event.getClickedBlock()
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isEmpty then
            return
        gm.check(player.getUniqueId(), rein.get.group, Permissions.Build) match
            case Right(ok) if ok =>
                return
            case _ =>
                ()

        // TODO: notify of permission denied
        val btype = loc.getType()

        if MaterialTags.SHOVELS.isTagged(slot.getType()) then
            // prevent grass -> path
            if btype == Material.GRASS_BLOCK then
                event.setCancelled(true)
            // prevent extinguishing campfires with shovels
            else if BlockSets.campfires.contains(btype) then
                val lightable = loc.getBlockData().asInstanceOf[Lightable]
                if lightable.isLit() then
                    event.setCancelled(true)
        // prevent making farmland
        else if MaterialTags.HOES.isTagged(slot.getType()) then
            if BlockSets.farmlandableBlocks.contains(btype) then
                event.setCancelled(true)
        // prevent stripping logs
        else if MaterialTags.AXES.isTagged(slot.getType()) then
            if BlockSets.logs.contains(btype) then
                event.setCancelled(true)
        // prevent harvesting beehives
        else if BlockSets.beehiveHarvestingTools.contains(slot.getType()) then
            if BlockSets.beehives.contains(btype) then
                event.setCancelled(true)
        // prevent modifiying candles
        else if BlockSets.candles.contains(btype) then
            // prevent extinguishing candles
            if !event.hasItem() then
                val lightable = loc.getBlockData().asInstanceOf[Lightable]
                if lightable.isLit() then
                    event.setCancelled(true)
            // prevent lighting candles
            else if BlockSets.igniters.contains(slot.getType()) then
                event.setCancelled(true)
        // prevent lighting campfires
        else if BlockSets.campfires.contains(btype) then
            val lightable = loc.getBlockData().asInstanceOf[Lightable]
            if !lightable.isLit() && BlockSets.igniters.contains(slot.getType()) then
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventModifiyingBeacon(event: PlayerInteractEvent): Unit =
        if !event.hasBlock() || event.getAction() != Action.RIGHT_CLICK_BLOCK then
            return
        if event.getClickedBlock().getType() != Material.BEACON then
            return

        val player = event.getPlayer()
        val loc = event.getClickedBlock()
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isEmpty then
            return

        // TODO: notify of permission denied
        gm.check(player.getUniqueId(), rein.get.group, Permissions.Build) match
            case Right(ok) if ok =>
                return
            case _ =>
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPuttingBookInLectern(event: PlayerInteractEvent): Unit =
        if event.getAction() != Action.RIGHT_CLICK_BLOCK then
            return
        val block = event.getClickedBlock()
        if block.getType() != Material.LECTERN then
            return
        val blockData = block.getBlockData().asInstanceOf[Lectern]
        if blockData.hasBook() then
            return
        val rein = brm.getReinforcement(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID())
        if rein.isEmpty then
            return
        val player = event.getPlayer()

        gm.check(player.getUniqueId(), rein.get.group, Permissions.Build) match
            case Right(ok) if ok =>
                return
            case _ =>
                event.setCancelled(true)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventTakingBookFromLectern(event: PlayerTakeLecternBookEvent): Unit =
        val block = event.getLectern().getBlock()
        val rein = brm.getReinforcement(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID())
        if rein.isEmpty then
            return
        val player = event.getPlayer()

        gm.check(player.getUniqueId(), rein.get.group, Permissions.Chests) match
            case Right(ok) if ok =>
                return
            case _ =>
                event.setCancelled(true)

    // prevent harvesting reinforced powdered snow w/out perms
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidPickup(event: PlayerBucketFillEvent): Unit =
        if event.getBlockClicked() != Material.POWDER_SNOW then
            return

        val block = event.getBlockClicked()
        val rein = brm.getReinforcement(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID())
        if rein.isEmpty then
            return
        val player = event.getPlayer()

        gm.check(player.getUniqueId(), rein.get.group, Permissions.RemoveReinforcements) match
            case Right(ok) if ok =>
                return
            case _ =>
                event.setCancelled(true)

    // prevent placing liquids in blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventLiquidPlace(event: PlayerBucketEmptyEvent): Unit =
        val block = event.getBlockClicked().getRelative(event.getBlockFace())
        // air isn't reinforceable, and solid blocks can't become waterlogged
        if block.getType() == Material.AIR || block.getType().isSolid() then
            return
        val rein = brm.getReinforcement(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID())

    // prevent bonemealing blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventBonemealing(event: BlockFertilizeEvent): Unit =
        val player = event.getPlayer()
        if player == null then
            return

        event.getBlocks().forEach { x =>
            val loc = BlockAdjustment.adjustBlock(x.getBlock())
            val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
            if rein.isDefined then
                gm.check(player.getUniqueId(), rein.get.group, Permissions.Crops) match
                    case Right(ok) if ok =>
                        ()
                    case _ =>
                        // TODO: notify of permission denied
                        event.setCancelled(true)
        }

    //
    //// Stuff that enforces reinforcements in the face of non-permissions; i.e. stuff like preventing water from killing reinforced blocks
    //

    // prevent pistons from pushing blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPistonPush(event: BlockPistonExtendEvent): Unit =
        event.getBlocks().forEach { x =>
            val loc = BlockAdjustment.adjustBlock(event.getBlock())
            val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
            if rein.isDefined then
                event.setCancelled(true)
        }

    // prevent pistons from pulling blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPistonPull(event: BlockPistonRetractEvent): Unit =
        event.getBlocks().forEach { x =>
            val loc = BlockAdjustment.adjustBlock(event.getBlock())
            val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
            if rein.isDefined then
                event.setCancelled(true)
        }
    
    // prevent fire from burning blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventFire(event: BlockBurnEvent): Unit =
        val loc = BlockAdjustment.adjustBlock(event.getBlock())
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isDefined then
            event.setCancelled(true)

    // prevent zombies from killing doors
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventZombies(event: EntityChangeBlockEvent): Unit =
        val loc = BlockAdjustment.adjustBlock(event.getBlock())
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isDefined then
            event.setCancelled(true)

    // prevent reinforced blocks from falling
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventFallingReinforcement(event: EntitySpawnEvent): Unit =
        if event.getEntityType() != EntityType.FALLING_BLOCK then
            return
        val block = event.getLocation().getBlock()
        val rein = brm.getReinforcement(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID())
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
        val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
        if rein.isDefined then
            event.setCancelled(true)

    // prevent plants from breaking reinforced blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def preventPlantGrowth(event: StructureGrowEvent): Unit =
        event.getBlocks().forEach { x =>
            val loc = x.getBlock()
            if brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID()).isDefined then
                event.setCancelled(true)
        }

    // have explosions damage blocks
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def onExplosion(event: EntityExplodeEvent): Unit =
        // the blockList is mutable and editing it will edit what blocks are destroyed
        val it = event.blockList().iterator()
        while it.hasNext() do
            val block = it.next()
            val loc = BlockAdjustment.adjustBlock(block)
            val rein = brm.getReinforcement(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getUID())
            if rein.isDefined then
                brm.break(loc.getX(), loc.getY(), loc.getZ(), loc.getType().getHardness(), loc.getWorld().getUID()) match
                    case Right(_) => it.remove()
                    case Left(_) => ()

                
