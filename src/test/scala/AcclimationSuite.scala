// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Acclimation.Storage
import BallCore.Storage.SQLManager
import BallCore.Storage.KeyVal
import BallCore.Storage.SQLKeyVal
import org.bukkit.plugin.Plugin
import BallCore.Beacons.CivBeaconManager
import be.seeseemelk.mockbukkit.ServerMock
import be.seeseemelk.mockbukkit.MockPlugin
import BallCore.Groups.GroupManager
import be.seeseemelk.mockbukkit.MockBukkit
import java.util.UUID
import org.bukkit.World
import org.bukkit.Location
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import be.seeseemelk.mockbukkit.WorldMock
import BallCore.Acclimation.AcclimationActor
import org.bukkit.entity.Player
import BallCore.Acclimation.AcclimationMessage
import scala.concurrent.ExecutionContext

class AcclimationSuite extends munit.FunSuite:
    val server: ServerMock = MockBukkit.mock()
    val plugin: MockPlugin = MockBukkit.createMockPlugin()

    // sets up a square with 20 long sides at (0, 0)
    def setup(user: UUID, world: World)(using gm: GroupManager, cbm: CivBeaconManager, sql: SQLManager): Unit =
        val beaconID = sql.useBlocking(cbm.placeHeart(Location(world, 0, 0, 0), user)).map(_._1).toOption.get
        val gf = GeometryFactory()
        val validPolygon = gf.createPolygon(
            Array(
                Coordinate(-10, -10),
                Coordinate(-10, 10),
                Coordinate(10, 10),
                Coordinate(10, -10),
                Coordinate(-10, -10),
            )
        )
        sql.useBlocking(cbm.updateBeaconPolygon(beaconID, world, validPolygon)).toOption.get

    def makeActor()(using sql: SQLManager): (Player, World, Storage, AcclimationActor) =
        given KeyVal = SQLKeyVal()
        given storage: Storage = Storage()
        given Plugin = plugin
        given GroupManager = GroupManager()
        given CivBeaconManager = CivBeaconManager()

        val player = server.addPlayer()
        val world = WorldMock()

        setup(player.getUniqueId(), world)

        (player, world, storage, AcclimationActor { _ =>
            new ExecutionContext:
                override def execute(runnable: Runnable): Unit =
                    runnable.run()
                override def reportFailure(cause: Throwable): Unit =
                    ???
        })

    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    sql.test("boosted acclimation") { implicit sql =>
        val (player, world, storage, actor) = makeActor()

        player.teleport(Location(world, 0, 0, 0))

        sql.useBlocking(storage.setLatitude(player.getUniqueId(), 500.0))

        for i <- 1 to 26 do
            actor.handle(AcclimationMessage.tick)

        val lat = sql.useBlocking(storage.getLatitude(player.getUniqueId()))
        assertEqualsDouble(lat, 0.0, 0.1, "latitude after 26 ticks")
    }
