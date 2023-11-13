// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore

import BallCore.Acclimation.AcclimationActor
import BallCore.Beacons.{Beacons, CivBeaconManager}
import BallCore.Chat.ChatActor
import BallCore.CraftingStations.CraftingStations
import BallCore.CustomItems.*
import BallCore.DataStructures.{Clock, ShutdownCallbacks, WallClock}
import BallCore.Gear.QuadrantGear
import BallCore.Groups.GroupManager
import BallCore.MapCloning.MapCloningListener
import BallCore.Mining.AntiCheeser
import BallCore.Ores.{CardinalOres, Furnace, QuadrantOres}
import BallCore.Plants.PlantBatchManager
import BallCore.PluginMessaging.Messaging
import BallCore.PolygonEditor.PolygonEditor
import BallCore.PolyhedraEditor.PolyhedraEditor
import BallCore.Reinforcements.{
    ChunkStateManager,
    EntityReinforcementManager,
    EntityStateManager,
    HologramManager,
}
import BallCore.Shops.Order
import BallCore.Sidebar.SidebarActor
import BallCore.Sigils.{CustomEntityManager, Sigil, SigilSlimeManager}
import BallCore.Storage.Config
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Server}
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import dev.jorel.commandapi.{CommandAPI, CommandAPIBukkitConfig}

import java.nio.file.Files
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

final class Main extends JavaPlugin:
    given sm: ShutdownCallbacks = ShutdownCallbacks()

    override def onEnable(): Unit =
        CommandAPI.onLoad(CommandAPIBukkitConfig(this))
        CommandAPI.onEnable()

        val dataDirectory = getDataFolder.toPath
        val loader = YamlConfigurationLoader
            .builder()
            .path(dataDirectory.resolve("config.yaml"))
            .build()
        Files.createDirectories(dataDirectory)
        val config = Try(loader.load()).get
        val databaseConfig = Config.from(config.node("database")) match
            case Left(err) =>
                throw Exception(s"failed to read config because $err")
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

        given server: Server = Bukkit.getServer

        given reg: ItemRegistry = BasicItemRegistry()

        given bm: BlockManager = KeyValBlockManager()

        given cem: CustomEntityManager = CustomEntityManager()

        given bam: SigilSlimeManager = SigilSlimeManager()

        given editor: PolygonEditor = new PolygonEditor()

        given editor3D: PolyhedraEditor = new PolyhedraEditor()

        given lib: ScoreboardLibrary =
            ScoreboardLibrary.loadScoreboardLibrary(this)

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
        chatCommands.group.register()
        chatCommands.global.register()
        chatCommands.local.register()
        GroupsCommand().node.register()
        CheatCommand().node.register()
        DoneCommand().node.register()
        PlantsCommand().node.register()
        Messaging.register()

    override def onDisable(): Unit =
        CommandAPI.onDisable()
        import scala.concurrent.ExecutionContext.Implicits.global
        val _ =
            Await.ready(sm.shutdown(), scala.concurrent.duration.Duration.Inf)
