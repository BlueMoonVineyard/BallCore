// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage
import BallCore.Groups
import java.{util => ju}
import BallCore.Groups.RoleManagementProgram
import BallCore.Groups.GroupManagementProgram

class GroupsUISuite extends munit.FunSuite:
    val nullUUID = ju.UUID(0, 0)

    test("role management program") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = Some("gus role management program"))
        given ts: TestUIServices = TestUIServices(this)
        implicit val gm: Groups.GroupManager = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()

        val gid = gm.createGroup(ownerID, "woot")
        val roles = gm.roles(gid).toOption.get
        val admin = roles.find { x => x.name == "Admin" }.get

        val ui = RoleManagementProgram()

        // make sure that everyone doesn't deleteable

        val model1 = ui.init(ui.Flags(gid, nullUUID, ownerID))
        val view1 = ui.view(model1)
        val query1 = view1 \\ "item" \ "displayname"
        assert(!query1.exists(_.text.contains("Delete Role")), query1)

        // make sure that other roles are deleteable

        val model2 = ui.init(ui.Flags(gid, admin.id, ownerID))
        val view2 = ui.view(model2)
        val query2 = view2 \\ "item" \ "displayname"
        assert(query2.exists(_.text.contains("Delete Role")), query2.toString)

        // ensure that it actually deletes the role and goes back to the group management UI

        val expect = ts.expectTransfer()
        val model3 = ui.update(Groups.RoleManagementMessage.DeleteRole, model2)
        assert(expect.isCompleted)

        val (prog, flags) = expect.value.get.get
        assert(prog.isInstanceOf[GroupManagementProgram], prog)

        // ensure that it affected the changes
        val newRoles = gm.roles(gid).toOption.get
        assert(!newRoles.exists(_.name == "Admin"), newRoles)
    }
