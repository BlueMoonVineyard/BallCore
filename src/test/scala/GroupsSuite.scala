// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Storage
import BallCore.Groups
import java.{util => ju}

class GroupsSuite extends munit.FunSuite:
    test("creating and deleting one-person group") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = true)
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val notOwnerID = ju.UUID.randomUUID()

        val gid = gm.createGroup(ownerID, "woot")

        val res1 = gm.deleteGroup(notOwnerID, gid)
        assert(res1 == Left(Groups.GroupError.MustBeOwner), res1)

        val res2 = gm.deleteGroup(ownerID, gid)
        assert(res2 == Right(()), res2)
    }
    test("basic permissions and role management") {
        given sql: Storage.SQLManager = Storage.SQLManager(test = true)
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
        given sql: Storage.SQLManager = Storage.SQLManager(test = true)
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
