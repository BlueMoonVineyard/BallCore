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
import BallCore.Reinforcements.*
import BallCore.Rest.{RestManager, IngameRestManagerHooks, RestManagerHooks}
import BallCore.Shops.Order
import BallCore.Sidebar.SidebarActor
import BallCore.Sigils.{CustomEntityManager, Sigil, SigilSlimeManager}
import BallCore.Storage.Config
import dev.jorel.commandapi.{CommandAPI, CommandAPIBukkitConfig}
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Server}
import org.spongepowered.configurate.yaml.YamlConfigurationLoader

import java.nio.file.Files
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try
import BallCore.Sigils.SlimePillarManager
import BallCore.Reinforcements.BustThroughTracker
import BallCore.Sigils.BattleManager
import BallCore.Sigils.BattleHooks
import BallCore.Sigils.GameBattleHooks
import BallCore.OneTimeTeleport.OneTimeTeleporter
import BallCore.OneTimeTeleport.GameOneTimeTeleporterHooks
import BallCore.RandomSpawner.RandomSpawn
import BallCore.SpawnInventory.SpawnBook
import BallCore.Gear.Tier1Gear
import BallCore.Elevator.Elevators
import BallCore.Fingerprints.FingerprintManager
import BallCore.CraftingStations.CraftingStation

final class Main extends JavaPlugin:
    given sm: ShutdownCallbacks = ShutdownCallbacks()

    override def onEnable(): Unit =
        try
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

            given sql: Storage.SQLManager =
                sm.addIO(Storage.SQLManager(databaseConfig))

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

            given cem: CustomEntityManager = new CustomEntityManager()

            given bam: SigilSlimeManager = SigilSlimeManager()
            given spm: SlimePillarManager = SlimePillarManager()
            given ingameBattleHooks: BattleHooks = GameBattleHooks()
            given battleManager: BattleManager = new BattleManager()
            given editor: PolygonEditor = new PolygonEditor()
            given ott: OneTimeTeleporter = OneTimeTeleporter(
                GameOneTimeTeleporterHooks()
            )

            given editor3D: PolyhedraEditor = new PolyhedraEditor()

            given lib: ScoreboardLibrary =
                ScoreboardLibrary.loadScoreboardLibrary(this)

            given sid: SidebarActor = SidebarActor()

            given busts: BustThroughTracker = BustThroughTracker()

            given restHooks: RestManagerHooks = IngameRestManagerHooks()
            given rest: RestManager = RestManager()
            given fingerprints: FingerprintManager =
                Fingerprints.Fingerprints.register()

            sid.startListener()

            Datekeeping.Datekeeping.startSidebarClock()
            Beacons.registerItems()
            QuadrantOres.registerItems()
            QuadrantGear.registerItems()
            CardinalOres.registerItems()
            Furnace.registerItems()
            Reinforcements.register()
            CustomItemListener.register()
            given List[CraftingStation] = CraftingStations.register()
            Rest.Rest.register()
            SpawnBook.register()
            SpawnInventory.Listener.register()
            Alloys.Tier1.register()
            Tier1Gear.registerItems()
            InformationGiver().register()

            given aa: AcclimationActor = AcclimationActor.register()
            given rs: RandomSpawn = RandomSpawn()

            PolyhedraEditor.register()
            PolygonEditor.register()
            Mining.Mining.register()
            RandomSpawner.Listener.register()
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
            val groupCommands = GroupsCommand()
            groupCommands.node.register()
            groupCommands.invitesNode.register()
            BindHeartCommand().node.register()
            GetHeart().node.register()
            BookCommand().node.register()
            CancelCommand().node.register()
            DeclareCommand().node.register()
            CheatCommand().node.register()
            DoneCommand().node.register()
            PlantsCommand().node.register()
            OTTCommand().node.register()
            Messaging.register()
            GammaCommand().node.register()
            OneTimeAdaptation().node.register()
            RestartTimer().register()
            Elevators.register()
            val msg = MessageCommand()
            msg.node.`override`()
            msg.replyNode.register()
            msg.meNode.`override`()
            MyFingerprintCommand().node.register()
            StationCommand().node.register()
        catch
            case _ =>
                getSLF4JLogger().error("Failed to start BallCore, shutting down...")
                Bukkit.getServer().shutdown()

    override def onDisable(): Unit =
        CommandAPI.onDisable()
        import scala.concurrent.ExecutionContext.Implicits.global
        val _ =
            Await.ready(sm.shutdown(), scala.concurrent.duration.Duration.Inf)
