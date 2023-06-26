// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore

import BallCore.Hearts.Hearts
import BallCore.UI

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.Command
import org.bukkit.entity.Player
import org.bukkit.Server
import scala.concurrent.ExecutionContext
import org.bukkit.Bukkit
import java.util.logging.Level
import BallCore.Groups.GroupManager
import BallCore.Ores.QuadrantOres
import BallCore.Ores.CardinalOres
import BallCore.Ores.Furnace
import BallCore.Gear.QuadrantGear
import BallCore.Reinforcements
import BallCore.Woodcutter.Woodcutter
import BallCore.Reinforcements.BlockReinforcementManager
import BallCore.Reinforcements.ChunkStateManager
import BallCore.DataStructures.Clock
import BallCore.DataStructures.WallClock
import BallCore.Reinforcements.HologramManager
import BallCore.Hearts.HeartNetworkManager
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.BasicItemRegistry
import BallCore.CustomItems.BlockManager
import BallCore.CustomItems.KeyValBlockManager
import BallCore.CustomItems.CustomItemListener
import BallCore.Reinforcements.EntityStateManager
import BallCore.Reinforcements.EntityReinforcementManager
import BallCore.CraftingStations.CraftingStations
import BallCore.Chat.ChatActor
import BallCore.Acclimation.AcclimationActor
import BallCore.Mining.AntiCheeser
import BallCore.DataStructures.ShutdownCallbacks
import BallCore.Plants.PlantBatchManager
import scala.concurrent.Await
import java.time.Duration
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import BallCore.Sidebar.SidebarActor
import BallCore.Sigils.Sigil
import BallCore.Sigils.BanishmentManager
import BallCore.Sigils.CustomEntityManager

final class Main extends JavaPlugin:
    given sql: Storage.SQLManager = new Storage.SQLManager
    given keyVal: Storage.SQLKeyVal = new Storage.SQLKeyVal
    given acclimation: Acclimation.Storage = new Acclimation.Storage
    given ballcore: Main = this
    given prompts: UI.Prompts = new UI.Prompts
    given gm: GroupManager = new GroupManager
    given csm: ChunkStateManager = new ChunkStateManager
    given esm: EntityStateManager = new EntityStateManager
    given clock: Clock = new WallClock
    given hm: HologramManager = new HologramManager
    given hn: HeartNetworkManager = new HeartNetworkManager
    given brm: BlockReinforcementManager = new BlockReinforcementManager
    given erm: EntityReinforcementManager = new EntityReinforcementManager
    given ac: AntiCheeser = new AntiCheeser
    given server: Server = Bukkit.getServer()
    given reg: ItemRegistry = BasicItemRegistry()
    given bm: BlockManager = KeyValBlockManager()
    given sm: ShutdownCallbacks = ShutdownCallbacks()
    given bam: BanishmentManager = BanishmentManager()
    given cem: CustomEntityManager = CustomEntityManager()
    
    override def onEnable() =
        given lib: ScoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this)
        given sid: SidebarActor = SidebarActor()
        sid.startListener()

        Datekeeping.Datekeeping.startSidebarClock()
        Hearts.registerItems()
        QuadrantOres.registerItems()
        QuadrantGear.registerItems()
        CardinalOres.registerItems()
        Furnace.registerItems()
        Reinforcements.Reinforcements.register()
        Woodcutter.registerItems()
        CustomItemListener.register()
        CraftingStations.register()
        AcclimationActor.register()
        Mining.Mining.register()
        Sigil.register()
        given pbm: PlantBatchManager = Plants.Plants.register()
        given ac: ChatActor = Chat.Chat.register()
        val chatCommands = ChatCommands()
        getCommand("group").setExecutor(chatCommands.Group)
        getCommand("global").setExecutor(chatCommands.Global)
        getCommand("local").setExecutor(chatCommands.Local)
        getCommand("groups").setExecutor(GroupsCommand())
        getCommand("cheat").setExecutor(CheatCommand())
    override def onDisable() =
        import scala.concurrent.ExecutionContext.Implicits.global
        Await.ready(sm.shutdown(), scala.concurrent.duration.Duration.Inf)
