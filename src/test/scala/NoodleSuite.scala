// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage.SQLManager
import BallCore.Groups.GroupManager
import java.util.UUID
import BallCore.NoodleEditor.NoodleManager
import BallCore.NoodleEditor.NoodleKey
import BallCore.Groups.nullUUID
import be.seeseemelk.mockbukkit.WorldMock
import scalax.collection.immutable.Graph
import org.bukkit.Location
import scalax.collection.edges.UnDiEdge
import org.locationtech.jts.geom.GeometryFactory
import scalax.collection.edges._
import scalax.collection.OuterImplicits._
import cats.effect.IO
import cats.effect.kernel.Resource
import skunk.Session

class NoodleSuite extends munit.CatsEffectSuite {
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)

    val world = WorldMock()
    val gf = GeometryFactory()

    sql.test("basic noodle functionality") { implicit sql =>
        given gm: GroupManager = GroupManager()
        val noodleManager = NoodleManager()
        val ownerID = UUID.randomUUID()

        val noodle = NoodleManager.convert(
            Graph(
                Location(world, 0, 1, 0) ~ Location(world, 5, 1, 0)
            )
        )

        val noodle2 = NoodleManager.convert(
            Graph(
                Location(world, 0, 10, 0) ~ Location(world, 5, 10, 0)
            )
        )

        sql.withS(sql.withTX(for {
            groupID <- gm.createGroup(ownerID, "test")
            key = NoodleKey(groupID, nullUUID)
            _ <- noodleManager
                .setNoodleFor(ownerID, key, noodle, world.getUID)
                .assert(_.isRight, "should be able to set noodle")

            resource = Resource.pure[IO, Session[IO]](summon[Session[IO]])

            _ <- noodleManager
                .noodleAt(Location(world, 0, 0, 0))(using resource)
                .assertEquals(Some(key), "noodle should contain that")

            _ <- noodleManager
                .noodleAt(Location(world, 0, 10, 0))(using resource)
                .assertEquals(
                    None,
                    "noodle should not contain that (going above)",
                )

            _ <- noodleManager
                .noodleAt(Location(world, 0, -10, 0))(using resource)
                .assertEquals(
                    None,
                    "noodle should not contain that (going below)",
                )

            _ <- noodleManager
                .getNoodleFor(key, world.getUID)
                .assert(_.isDefined, "noodle should be persisted to database")

            _ <- noodleManager
                .setNoodleFor(ownerID, key, noodle2, world.getUID)
                .assert(_.isRight, "should be able to set noodle again")

            _ <- noodleManager
                .noodleAt(Location(world, 0, 10, 0))(using resource)
                .assertEquals(Some(key), "noodle should contain that")

            _ <- noodleManager
                .noodleAt(Location(world, 0, 20, 0))(using resource)
                .assertEquals(
                    None,
                    "noodle should not contain that (going above)",
                )

            _ <- noodleManager
                .noodleAt(Location(world, 0, 0, 0))(using resource)
                .assertEquals(
                    None,
                    "noodle should not contain that (going below)",
                )
        } yield ()))
    }
    test("a simple noodle can be recovered from jts") {
        val graph = Graph(
            Location(world, 0, 1, 0) ~ Location(world, 5, 1, 0)
        )
        val noodle = NoodleManager.convert(graph)
        val recoveredGraph = NoodleManager.recover(noodle, world)
        assertEquals(graph, recoveredGraph)
    }
    test("a y-shaped noodle can be recovered from jts") {
        val graph = Graph(
            Location(world, 0, 1, 0) ~ Location(world, 5, 1, 0),
            Location(world, 5, 1, 0) ~ Location(world, 7, 1, 10),
            Location(world, 5, 1, 0) ~ Location(world, 7, 1, -10),
        )
        val noodle = NoodleManager.convert(graph)
        val recoveredGraph = NoodleManager.recover(noodle, world)
        assertEquals(graph, recoveredGraph)
    }
}
