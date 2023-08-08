// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage
import BallCore.Groups
import java.{util => ju}
import BallCore.Groups.Permissions
import BallCore.Groups.RuleMode

class GroupsSuite extends munit.FunSuite:
    test("creating and deleting one-person group") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = Some("gs creating and deleting one-person group"))
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val notOwnerID = ju.UUID.randomUUID()

        val gid = gm.createGroup(ownerID, "woot")

        val res1 = gm.deleteGroup(notOwnerID, gid)
        assert(res1 == Left(Groups.GroupError.MustBeOwner), res1)

        val res2 = gm.deleteGroup(ownerID, gid)
        assert(res2 == Right(()), res2)
    }
    test("people can't modify their own permissions") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = Some("people can't modify their own permissions"))
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val memberID = ju.UUID.randomUUID()

        val gid = gm.createGroup(ownerID, "woot!")

        val roles = gm.roles(gid)
        assert(roles.isRight, roles)
        val actualRoles = roles.getOrElse(List())
        val modRoleID = actualRoles.find { x => x.name == "Moderator" }.get.id

        gm.sudoSetRolePermissions(gid, modRoleID, Map(
            Permissions.ManageRoles -> RuleMode.Allow,
            Permissions.RemoveUser -> RuleMode.Allow,
        ))

        assertEquals(gm.addToGroup(memberID, gid), Right(()))
        assertEquals(gm.assignRole(ownerID, memberID, gid, modRoleID, true), Right(()))

        assertEquals(gm.setRolePermissions(memberID, gid, modRoleID, Map(
            Permissions.InviteUser -> RuleMode.Allow,
        )), Left(Groups.GroupError.RoleAboveYours))
    }
    test("people can't give out permissions they don't have") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = Some("gs people can't give out permissions they don't have"))
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val memberID = ju.UUID.randomUUID()

        val gid = gm.createGroup(ownerID, "woot!")

        val roles = gm.roles(gid)
        assert(roles.isRight, roles)
        val actualRoles = roles.getOrElse(List())
        val modRoleID = actualRoles.find { x => x.name == "Moderator" }.get.id
        val everyoneID = actualRoles.find { x => x.name == "Everyone" }.get.id

        gm.sudoSetRolePermissions(gid, modRoleID, Map(
            Permissions.ManageRoles -> RuleMode.Allow,
            Permissions.RemoveUser -> RuleMode.Allow,
        ))

        assertEquals(gm.addToGroup(memberID, gid), Right(()))
        assertEquals(gm.assignRole(ownerID, memberID, gid, modRoleID, true), Right(()))

        assertEquals(gm.setRolePermissions(memberID, gid, everyoneID, Map(
            Permissions.InviteUser -> RuleMode.Allow,
        )), Left(Groups.GroupError.MustHavePermission))
        assertEquals(gm.setRolePermissions(memberID, gid, everyoneID, Map(
            Permissions.RemoveUser -> RuleMode.Allow,
        )), Right(()))
    }
    test("basic permissions and role management") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = Some("gs basic permissions and role management"))
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val notMemberID = ju.UUID.randomUUID()

        val gid = gm.createGroup(ownerID, "woot!")

        val res1 = gm.check(ownerID, gid, Groups.Permissions.ManageUserRoles)
        assert(res1 == Right(true), res1)

        val res2 = gm.check(notMemberID, gid, Groups.Permissions.ManageUserRoles)
        assert(res2 == Right(false), res2)

        val res3 = gm.addToGroup(notMemberID, gid)
        assert(res3 == Right(()), res3)

        val roles = gm.roles(gid)
        assert(roles.isRight, roles)
        val actualRoles = roles.getOrElse(List())
        val adminRoleID = actualRoles.find { x => x.name == "Admin" }.get.id
        val modRoleID = actualRoles.find { x => x.name == "Moderator" }.get.id

        val res4 = gm.check(notMemberID, gid, Groups.Permissions.ManageUserRoles)
        assert(res4 == Right(false), res4)

        val res5 = gm.assignRole(ownerID, notMemberID, gid, modRoleID, true)
        assert(res5 == Right(()), res5)

        val res6 = gm.check(notMemberID, gid, Groups.Permissions.ManageUserRoles)
        assert(res6 == Right(true), res6)

        val res7 = gm.assignRole(notMemberID, notMemberID, gid, adminRoleID, true)
        assert(res7 == Left(Groups.GroupError.RoleAboveYours), res7)

        val res8 = gm.assignRole(notMemberID, notMemberID, gid, modRoleID, false)
        assert(res8 == Left(Groups.GroupError.RoleAboveYours), res8)
    }
    test("multi-owner groups") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = Some("gs multi-owner groups"))
        val gm = Groups.GroupManager()

        val owner1Id = ju.UUID.randomUUID()
        val gid = gm.createGroup(owner1Id, "woot")

        val owner2Id = ju.UUID.randomUUID()
        val res1 = gm.promoteToOwner(owner2Id, owner1Id, gid)
        assert(res1 == Left(Groups.GroupError.MustBeOwner), res1)

        val res2 = gm.promoteToOwner(owner1Id, owner2Id, gid)
        assert(res2 == Left(Groups.GroupError.TargetNotInGroup), res2)

        val res3 = gm.addToGroup(owner2Id, gid)
        assert(res3 == Right(()), res3)

        val res4 = gm.promoteToOwner(owner1Id, owner2Id, gid)
        assert(res4 == Right(()), res4)

        val res5 = gm.giveUpOwnership(owner1Id, gid)
        assert(res5 == Right(()), res5)

        val res6 = gm.giveUpOwnership(owner2Id, gid)
        assert(res6 == Left(Groups.GroupError.GroupWouldHaveNoOwners), res6)
    }
