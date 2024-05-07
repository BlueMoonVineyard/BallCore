// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed

import CivCubed.Acclimation.AcclimationActor
import CivCubed.Beacons.{Beacons, CivBeaconManager, BeaconManagerHooks}
import CivCubed.Chat.ChatActor
import CivCubed.CraftingStations.CraftingStations
import CivCubed.CustomItems.*
import CivCubed.DataStructures.{Clock, ShutdownCallbacks, WallClock}
import CivCubed.Ores.Furnace
import CivCubed.Groups.GroupManager
import CivCubed.MapCloning.MapCloningListener
import CivCubed.Mining.AntiCheeser
import CivCubed.Plants.PlantBatchManager
import CivCubed.PluginMessaging.Messaging
import CivCubed.PolygonEditor.PolygonEditor
import CivCubed.PolyhedraEditor.PolyhedraEditor
import CivCubed.Reinforcements.*
import CivCubed.Rest.{RestManager, IngameRestManagerHooks, RestManagerHooks}
import CivCubed.Shops.Order
import CivCubed.Sidebar.SidebarActor
import CivCubed.Sigils.{CustomEntityManager, Sigil, SigilSlimeManager}
import CivCubed.Storage.Config
import dev.jorel.commandapi.{CommandAPI, CommandAPIBukkitConfig}
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Server}
import org.spongepowered.configurate.yaml.YamlConfigurationLoader

import java.nio.file.Files
import scala.util.Try
import CivCubed.Sigils.SlimePillarManager
import CivCubed.Reinforcements.BustThroughTracker
import CivCubed.Sigils.BattleManager
import CivCubed.Sigils.GameBattleHooks
import CivCubed.OneTimeTeleport.OneTimeTeleporter
import CivCubed.OneTimeTeleport.GameOneTimeTeleporterHooks
import CivCubed.RandomSpawner.RandomSpawn
import CivCubed.SpawnInventory.SpawnBook
import CivCubed.Gear.Tier1Gear
import CivCubed.Elevator.Elevators
import CivCubed.Fingerprints.FingerprintManager
import CivCubed.CraftingStations.CraftingStation
import io.sentry.Sentry
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import com.destroystokyo.paper.event.server.ServerExceptionEvent
import cats.effect.IO
import CivCubed.WebHooks.WebHookManager
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import cats.effect.kernel.Deferred
import CivCubed.Beacons.IngameBeaconManagerHooks
import CivCubed.PrimeTime.PrimeTimeManager
import CivCubed.NoodleEditor.NoodleManager
import CivCubed.Gear.SkyBronzeGear
import CivCubed.Gear.AdamantiteGear
import CivCubed.Gear.SunoGear
import CivCubed.Gear.HepatizonGear
import CivCubed.Gear.ManyullynGear
import CivCubed.Commands.*
import CivCubed.PVPServer.ArmorStandDispenser

class ExceptionLogger extends Listener:
    @EventHandler
    def serverExceptionEvent(event: ServerExceptionEvent): Unit =
        event.getException().printStackTrace()
        Sentry.captureException(event.getException())
        ()

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

            Sentry.init(options => {
                options.setDsn(config.node("sentry", "dsn").getString())
                options.setTracesSampleRate(0.2)
            })

            val databaseConfig = Config.from(config.node("database")) match
                case Left(err) =>
                    throw Exception(s"failed to read config because $err")
                case Right(value) =>
                    value

            given sql: Storage.SQLManager =
                sm.addIO(Storage.SQLManager(databaseConfig))

            given client: Client[IO] =
                sm.addIO(
                    EmberClientBuilder
                        .default[IO]
                        .build
                        .allocated
                        .unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
                )

            given keyVal: Storage.SQLKeyVal = new Storage.SQLKeyVal

            given acclimation: Acclimation.Storage = new Acclimation.Storage

            given civcubed: Main = this

            given prompts: UI.Prompts = new UI.Prompts

            given gm: GroupManager = new GroupManager

            given csm: ChunkStateManager = new ChunkStateManager

            given esm: EntityStateManager = new EntityStateManager

            given clock: Clock = new WallClock

            given hm: HologramManager = new HologramManager

            given beaconHooks: Deferred[IO, BeaconManagerHooks] =
                Deferred[IO, BeaconManagerHooks].unsafeRunSync()(
                    cats.effect.unsafe.IORuntime.global
                )

            given hn: CivBeaconManager = new CivBeaconManager

            given erm: EntityReinforcementManager =
                new EntityReinforcementManager

            given ac: AntiCheeser = new AntiCheeser

            given server: Server = Bukkit.getServer

            given reg: ItemRegistry = BasicItemRegistry()

            given bm: BlockManager = KeyValBlockManager()

            given cem: CustomEntityManager = new CustomEntityManager()

            given bam: SigilSlimeManager = SigilSlimeManager()
            given ingameBattleHooks: GameBattleHooks = GameBattleHooks()
            given battleManager: BattleManager = new BattleManager()
            given spm: SlimePillarManager = SlimePillarManager()

            beaconHooks
                .complete(IngameBeaconManagerHooks())
                .unsafeRunSync()(cats.effect.unsafe.IORuntime.global)

            given editor: PolygonEditor = new PolygonEditor()
            given ott: OneTimeTeleporter = OneTimeTeleporter(
                GameOneTimeTeleporterHooks()
            )
            given webhooks: WebHookManager = WebHookManager()

            given editor3D: PolyhedraEditor = new PolyhedraEditor()

            given lib: ScoreboardLibrary =
                ScoreboardLibrary.loadScoreboardLibrary(this)

            given sid: SidebarActor = SidebarActor()

            given busts: BustThroughTracker = BustThroughTracker()

            given restHooks: RestManagerHooks = IngameRestManagerHooks()
            given rest: RestManager = RestManager()
            given fingerprints: FingerprintManager =
                Fingerprints.Fingerprints.register()
            given primeTime: PrimeTimeManager = PrimeTimeManager()

            sid.startListener()
            getServer()
                .getPluginManager()
                .registerEvents(ExceptionLogger(), this)

            CompressedCobblestone.register()
            Datekeeping.Datekeeping.startSidebarClock()
            Furnace.registerItems()
            CustomItemListener.register()
            given List[CraftingStation] = CraftingStations.register()
            Rest.Rest.register()
            SpawnBook.register()
            SpawnInventory.Listener.register()
            Alloys.Tier1.register()
            Alloys.Tier2.register()
            Tier1Gear.registerItems()
            SkyBronzeGear.registerItems()
            AdamantiteGear.register()
            InformationGiver().register()
            SunoGear.registerItems()
            HepatizonGear.registerItems()
            Ferrobyte.Ferrobyte.registerItems()
            ManyullynGear.registerItems()

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

            val (
                noodleEditor,
                noodleManager,
                essenceManager,
                delinquency,
                drainer,
            ) =
                NoodleEditor.NoodleEditor.register()
            given NoodleEditor.NoodleEditor = noodleEditor
            given NoodleEditor.NoodleManager = noodleManager
            given NoodleEditor.EssenceManager = essenceManager
            given NoodleEditor.DelinquencyManager = delinquency
            given NoodleEditor.EssenceDrainer = drainer

            Beacons.register()
            Reinforcements.register()

            CardinalCommand().tree.register()
            val chatCommands = ChatCommands()
            Order.register()
            // HTTP.register()
            chatCommands.group.register()
            chatCommands.global.register()
            chatCommands.local.register()
            BindHeartCommand().node.register()
            val groupCommands = GroupsCommand()
            groupCommands.node.register()
            groupCommands.invitesNode.register()
            groupCommands.cancelBattleNode.register()
            groupCommands.viewNearbyClaimsNode.register()
            GetHeart().node.register()
            BookCommand().node.register()
            CancelCommand().node.register()
            DeclareCommand().node.register()
            CheatCommand().node.register()
            DoneCommand().node.register()
            PlantsCommand().node.register()
            OTTCommand().node.register()
            GammaCommand().node.register()
            OneTimeAdaptation().node.register()
            Messaging.register()
            RestartTimer().register()
            Elevators.register()
            MiscRecipes.register()
            val msg = MessageCommand()
            msg.node.`override`()
            msg.replyNode.register()
            msg.meNode.`override`()
            MyFingerprintCommand().node.register()
            StationCommand().node.register()
            RelayCommand().root.register()
            SettingsCommand().node.register()
            VoteCommand().tree.register()
            ArmorStandDispenser.register()
        catch
            case e: Throwable =>
                getSLF4JLogger().error(
                    "Failed to start CivCubed, shutting down...",
                    e,
                )
                Bukkit.getServer().shutdown()

    override def onDisable(): Unit =
        CommandAPI.onDisable()
        import cats.effect.unsafe.implicits.global
        sm.shutdown()
            .redeemWith(
                { case exception: Throwable =>
                    IO {
                        Sentry.captureException(exception)
                        getSLF4JLogger()
                            .error("Failed to shut down CivCubed", exception)
                    }
                },
                { _ =>
                    IO {
                        getSLF4JLogger().info("Successfully shut down CivCubed")
                    }
                },
            )
            .unsafeRunSync()
