// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore

import BallCore.Beacons.Beacons
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Server
import scala.concurrent.ExecutionContext
import org.bukkit.Bukkit
import BallCore.Groups.GroupManager
import BallCore.Ores.QuadrantOres
import BallCore.Ores.CardinalOres
import BallCore.Ores.Furnace
import BallCore.Gear.QuadrantGear
import BallCore.Reinforcements.ChunkStateManager
import BallCore.DataStructures.Clock
import BallCore.DataStructures.WallClock
import BallCore.Reinforcements.HologramManager
import BallCore.Beacons.CivBeaconManager
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
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import BallCore.Sidebar.SidebarActor
import BallCore.Sigils.Sigil
import BallCore.Sigils.CustomEntityManager
import BallCore.Sigils.SigilSlimeManager
import BallCore.PolygonEditor.PolygonEditor
import BallCore.MapCloning.MapCloningListener
import BallCore.PolyhedraEditor.PolyhedraEditor
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import scala.util.Try
import BallCore.Storage.Config
import BallCore.Shops.Order

final class Main extends JavaPlugin:
    given sm: ShutdownCallbacks = ShutdownCallbacks()

    override def onEnable() =
        val dataDirectory = getDataFolder().toPath()
        val loader = YamlConfigurationLoader.builder()
            .path(dataDirectory.resolve("config.yaml"))
            .build()
        Files.createDirectories(dataDirectory)
        val config = Try(loader.load()).get
        val databaseConfig = Config.from(config.node("database")) match
            case Left(err) =>
                throw Exception(s"failed to read config because ${err}")
            case Right(value) =>
                value

        given sql: Storage.SQLManager = Storage.SQLManager(databaseConfig)
        given keyVal: Storage.SQLKeyVal = new Storage.SQLKeyVal
        given acclimation: Acclimation.Storage = new Acclimation.Storage
        given ballcore: Main = this
        given prompts: UI.Prompts = new UI.Prompts
        given gm: GroupManager = new GroupManager
        given csm: ChunkStateManager = new ChunkStateManager
        given esm: EntityStateManager = new EntityStateManager
        given clock: Clock = new WallClock
        given hm: HologramManager = new HologramManager
        given hn: CivBeaconManager = new CivBeaconManager
        given erm: EntityReinforcementManager = new EntityReinforcementManager
        given ac: AntiCheeser = new AntiCheeser
        given server: Server = Bukkit.getServer()
        given reg: ItemRegistry = BasicItemRegistry()
        given bm: BlockManager = KeyValBlockManager()
        given cem: CustomEntityManager = CustomEntityManager()
        given bam: SigilSlimeManager = SigilSlimeManager()
        given editor: PolygonEditor = new PolygonEditor()
        given editor3D: PolyhedraEditor = new PolyhedraEditor()

        given lib: ScoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this)
        given sid: SidebarActor = SidebarActor()
        sid.startListener()

        Datekeeping.Datekeeping.startSidebarClock()
        Beacons.registerItems()
        QuadrantOres.registerItems()
        QuadrantGear.registerItems()
        CardinalOres.registerItems()
        Furnace.registerItems()
        Reinforcements.Reinforcements.register()
        CustomItemListener.register()
        CraftingStations.register()
        given aa: AcclimationActor = AcclimationActor.register()
        PolyhedraEditor.register()
        PolygonEditor.register()
        Mining.Mining.register()
        MapCloningListener.register()
        Sigil.register()
        given pbm: PlantBatchManager = Plants.Plants.register()
        given chatActor: ChatActor = Chat.Chat.register()
        val chatCommands = ChatCommands()
        Order.register()
        // HTTP.register()
        getCommand("group").setExecutor(chatCommands.Group)
        getCommand("global").setExecutor(chatCommands.Global)
        getCommand("local").setExecutor(chatCommands.Local)
        getCommand("groups").setExecutor(new GroupsCommand)
        getCommand("cheat").setExecutor(CheatCommand())
        getCommand("done").setExecutor(DoneCommand())
        getCommand("plants").setExecutor(PlantsCommand())
    override def onDisable() =
        import scala.concurrent.ExecutionContext.Implicits.global
        val _ = Await.ready(sm.shutdown(), scala.concurrent.duration.Duration.Inf)
