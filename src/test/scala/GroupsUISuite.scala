// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage
import BallCore.Groups
import java.{util => ju}
import BallCore.Groups.RoleManagementProgram
import BallCore.UI.TestUIServices

class GroupsUISuite extends munit.FunSuite:
    val nullUUID = ju.UUID(0, 0)

    test("role management program") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = true)
        given ts: TestUIServices = TestUIServices(
            prompt => {
                assert(false, "program should not prompt")
                ???
            },
            (_, _) => assert(false, "program should not transfer to another program"),
        )
        implicit val gm: Groups.GroupManager = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()

        val gid = gm.createGroup(ownerID, "woot")

        val ui = RoleManagementProgram()

        val model1 = ui.init(ui.Flags(gid, nullUUID, ownerID))
        val view1 = ui.view(model1)
    }
