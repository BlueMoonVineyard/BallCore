// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import java.{util => ju}
import be.seeseemelk.mockbukkit.MockBukkit
import BallCore.PolygonEditor.Editor
import org.bukkit.plugin.Plugin
import be.seeseemelk.mockbukkit.WorldMock
import org.bukkit.Location
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.util.GeometricShapeFactory
import BallCore.PolygonEditor._

class PolygonSuite extends munit.FunSuite:
    val server = MockBukkit.mock()
    val plugin = MockBukkit.createMockPlugin()
    val player = server.addPlayer()
    given Plugin = plugin

    test("editor integration") {
        val editor = Editor()
        val world = WorldMock()

        editor.create(player)

        val rect = List(
            (0, 0),
            (0, 3),
            (3, 3),
            (3, 0),
        )

        rect.foreach { (x, z) =>
            val loc = Location(world, x, 0, z)
            assertEquals(editor.clicked(player, loc), true)
        }

        val firstCorner = Location(world, 0, 0, 0)
        val badCorner = Location(world, 4, 0, 2)
        editor.look(player, firstCorner)
        editor.clicked(player, firstCorner)
        editor.look(player, badCorner)
        editor.clicked(player, firstCorner)
    }
    test("editormodel") {
        import BallCore.PolygonEditor.EditorMsg._
        import BallCore.PolygonEditor.EditorModelState._
        import BallCore.PolygonEditor.EditorAction._

        val world = WorldMock()

        val rect = List(
            (0, 0),
            (0, 3),
            (3, 3),
            (3, 0),
        )
        val locs = rect.map((x, z) => Location(world, x, 0, z))

        val initialEM = EditorModel(locs)

        var invalidationEM = (initialEM, List[EditorAction]())

        invalidationEM = invalidationEM._1.update(look(locs(0)))
        assertEquals(invalidationEM._1.state, lookingAt(locs(0)))
        val beforeInvalid = invalidationEM._1.polygon
        invalidationEM = invalidationEM._1.update(rightClick())
        assertEquals(invalidationEM._1.state, editingPoint(0, true))
        invalidationEM = invalidationEM._1.update(look( Location(world, 4, 0, 2) ))
        assertEquals(invalidationEM._1.state, editingPoint(0, false))
        invalidationEM = invalidationEM._1.update(rightClick())
        assertEquals(invalidationEM._1.state, idle())
        val afterInvalid = invalidationEM._1.polygon
        assertEquals(beforeInvalid, afterInvalid)

        var deletionEM = (initialEM, List[EditorAction]())

        deletionEM = deletionEM._1.update(look(locs(0)))
        assertEquals(deletionEM._1.state, lookingAt(locs(0)))
        val beforeDeletion = deletionEM._1.polygon
        deletionEM = deletionEM._1.update(leftClick())
        assert(deletionEM._2.length == 1, deletionEM)
    }