// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.Groups.{Permissions, RuleMode, everyoneUUID, nullUUID}
import BallCore.Storage.SQLManager
import BallCore.{Groups, Storage}

import java.util as ju

class GroupsSuite extends munit.FunSuite:
    val sql: FunFixture[SQLManager] =
        FunFixture[SQLManager](TestDatabase.setup, TestDatabase.teardown)
    sql.test("creating and deleting one-person group") { implicit sql =>
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val notOwnerID = ju.UUID.randomUUID()

        val gid = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(ownerID, "woot")))
        )

        val res1 =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.deleteGroup(notOwnerID, gid).value))
            )
        assert(res1 == Left(Groups.GroupError.MustBeOwner), res1)

        val res2 =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.deleteGroup(ownerID, gid).value))
            )
        assert(res2 == Right(()), res2)
    }
    sql.test("people can't modify their own permissions") { implicit sql =>
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val memberID = ju.UUID.randomUUID()

        val gid = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(ownerID, "woot!")))
        )

        val roles = sql.useBlocking(sql.withS(sql.withTX(gm.roles(gid).value)))
        assert(roles.isRight, roles)
        val actualRoles = roles.getOrElse(List())
        val modRoleID = actualRoles.find { x => x.name == "Moderator" }.get.id

        sql.useBlocking(
            sql.withS(
                gm.sudoSetRolePermissions(
                    gid,
                    modRoleID,
                    Map(
                        Permissions.ManageRoles -> RuleMode.Allow,
                        Permissions.RemoveUser -> RuleMode.Allow,
                    ),
                )
            )
        )

        assertEquals(
            sql.useBlocking(
                sql.withS(sql.withTX(gm.addToGroup(memberID, gid).value))
            ),
            Right(()),
        )
        assertEquals(
            sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        gm.assignRole(ownerID, memberID, gid, modRoleID, true)
                            .value
                    )
                )
            ),
            Right(()),
        )

        assertEquals(
            sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        gm.setRolePermissions(
                            memberID,
                            gid,
                            modRoleID,
                            Map(
                                Permissions.InviteUser -> RuleMode.Allow
                            ),
                        ).value
                    )
                )
            ),
            Left(Groups.GroupError.RoleAboveYours),
        )
    }
    sql.test("people can't give out permissions they don't have") {
        implicit sql =>
            val gm = Groups.GroupManager()
            val ownerID = ju.UUID.randomUUID()
            val memberID = ju.UUID.randomUUID()

            val gid =
                sql.useBlocking(
                    sql.withS(sql.withTX(gm.createGroup(ownerID, "woot!")))
                )

            val roles =
                sql.useBlocking(sql.withS(sql.withTX(gm.roles(gid).value)))
            assert(roles.isRight, roles)
            val actualRoles = roles.getOrElse(List())
            val modRoleID =
                actualRoles.find { x => x.name == "Moderator" }.get.id
            val everyoneID =
                actualRoles.find { x => x.name == "Everyone" }.get.id

            sql.useBlocking(
                sql.withS(
                    gm.sudoSetRolePermissions(
                        gid,
                        modRoleID,
                        Map(
                            Permissions.ManageRoles -> RuleMode.Allow,
                            Permissions.RemoveUser -> RuleMode.Allow,
                        ),
                    )
                )
            )

            assertEquals(
                sql.useBlocking(
                    sql.withS(sql.withTX(gm.addToGroup(memberID, gid).value))
                ),
                Right(()),
            )
            assertEquals(
                sql.useBlocking(
                    sql.withS(
                        sql.withTX(
                            gm.assignRole(
                                ownerID,
                                memberID,
                                gid,
                                modRoleID,
                                true,
                            ).value
                        )
                    )
                ),
                Right(()),
            )

            assertEquals(
                sql.useBlocking(
                    sql.withS(
                        sql.withTX(
                            gm.setRolePermissions(
                                memberID,
                                gid,
                                everyoneID,
                                Map(
                                    Permissions.InviteUser -> RuleMode.Allow
                                ),
                            ).value
                        )
                    )
                ),
                Left(Groups.GroupError.MustHavePermission),
            )
            assertEquals(
                sql.useBlocking(
                    sql.withS(
                        sql.withTX(
                            gm.setRolePermissions(
                                memberID,
                                gid,
                                everyoneID,
                                Map(
                                    Permissions.RemoveUser -> RuleMode.Allow
                                ),
                            ).value
                        )
                    )
                ),
                Right(()),
            )
    }
    sql.test("basic permissions and role management") { implicit sql =>
        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val moderatorID = ju.UUID.randomUUID()
        val randoID = ju.UUID.randomUUID()

        val gid = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(ownerID, "woot!")))
        )

        val res1 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(
                        ownerID,
                        gid,
                        nullUUID,
                        Groups.Permissions.ManageUserRoles,
                    ).value
                )
            )
        )
        assert(res1 == Right(true), res1)

        val res2 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(
                        moderatorID,
                        gid,
                        nullUUID,
                        Groups.Permissions.ManageUserRoles,
                    ).value
                )
            )
        )
        assert(res2 == Right(false), res2)

        val res3 =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.addToGroup(moderatorID, gid).value))
            )
        assert(res3 == Right(()), res3)

        val roles = sql.useBlocking(sql.withS(sql.withTX(gm.roles(gid).value)))
        assert(roles.isRight, roles)
        val actualRoles = roles.getOrElse(List())
        val adminRoleID = actualRoles.find { x => x.name == "Admin" }.get.id
        val modRoleID = actualRoles.find { x => x.name == "Moderator" }.get.id

        val res4 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(
                        moderatorID,
                        gid,
                        nullUUID,
                        Groups.Permissions.ManageUserRoles,
                    ).value
                )
            )
        )
        assert(res4 == Right(false), res4)

        val res5 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.assignRole(ownerID, moderatorID, gid, modRoleID, true)
                        .value
                )
            )
        )
        assert(res5 == Right(()), res5)

        val res6 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(
                        moderatorID,
                        gid,
                        nullUUID,
                        Groups.Permissions.ManageUserRoles,
                    ).value
                )
            )
        )
        assert(res6 == Right(true), res6)

        val res7 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.assignRole(
                        moderatorID,
                        moderatorID,
                        gid,
                        adminRoleID,
                        true,
                    ).value
                )
            )
        )
        assert(res7 == Left(Groups.GroupError.RoleAboveYours), res7)

        val res8 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.assignRole(
                        moderatorID,
                        moderatorID,
                        gid,
                        modRoleID,
                        false,
                    ).value
                )
            )
        )
        assert(res8 == Left(Groups.GroupError.RoleAboveYours), res8)

        val res9 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(
                        moderatorID,
                        gid,
                        nullUUID,
                        Groups.Permissions.Build,
                    ).value
                )
            )
        )
        assert(res9 == Right(true), res9)

        val res10 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(randoID, gid, nullUUID, Groups.Permissions.Build)
                        .value
                )
            )
        )
        assert(res10 == Right(false), res10)
    }
    sql.test("kicking") { implicit sql =>
        // setup

        val gm = Groups.GroupManager()
        val ownerID = ju.UUID.randomUUID()
        val moderator1ID = ju.UUID.randomUUID()
        val moderator2ID = ju.UUID.randomUUID()
        val rando1ID = ju.UUID.randomUUID()
        val rando2ID = ju.UUID.randomUUID()

        val gid = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(ownerID, "woot!")))
        )

        List(moderator1ID, moderator2ID, rando1ID, rando2ID).foreach { user =>
            val res =
                sql.useBlocking(
                    sql.withS(sql.withTX(gm.addToGroup(user, gid).value))
                )
            assert(res == Right(()), res)
        }

        val roles = sql.useBlocking(sql.withS(sql.withTX(gm.roles(gid).value)))
        assert(roles.isRight, roles)
        val actualRoles = roles.getOrElse(List())
        val modRoleID = actualRoles.find { x => x.name == "Moderator" }.get.id

        List(moderator1ID, moderator2ID).foreach { user =>
            val res = sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        gm.assignRole(ownerID, user, gid, modRoleID, true).value
                    )
                )
            )
            assert(res == Right(()), res)
        }

        // actual testing

        val res1 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.kickUserFromGroup(
                        moderator1ID,
                        ownerID,
                        gid,
                    )
                )
            )
        )
        assertEquals(
            res1,
            Left(Groups.GroupError.TargetIsAboveYou),
            "mods can't kick owners",
        )

        val res3 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.kickUserFromGroup(
                        rando2ID,
                        rando1ID,
                        gid,
                    )
                )
            )
        )
        assertEquals(
            res3,
            Left(Groups.GroupError.NoPermissions),
            "randos don't have perms",
        )

        val res2 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.kickUserFromGroup(
                        moderator1ID,
                        rando1ID,
                        gid,
                    )
                )
            )
        )
        assertEquals(res2, Right(()), "mods can kick randos")

        val res4 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.kickUserFromGroup(
                        rando1ID,
                        rando2ID,
                        gid,
                    )
                )
            )
        )
        assertEquals(
            res4,
            Left(Groups.GroupError.MustBeInGroup),
            "people outside can't kick",
        )

        val res5 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.kickUserFromGroup(
                        moderator1ID,
                        moderator2ID,
                        gid,
                    )
                )
            )
        )
        assertEquals(
            res5,
            Left(Groups.GroupError.TargetIsAboveYou),
            "mods can't kick eachother",
        )

        val res6 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.kickUserFromGroup(
                        ownerID,
                        moderator1ID,
                        gid,
                    )
                )
            )
        )
        assertEquals(res6, Right(()), "owners can kick mods")
    }
    sql.test("multi-owner groups") { implicit sql =>
        val gm = Groups.GroupManager()

        val owner1Id = ju.UUID.randomUUID()
        val gid = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(owner1Id, "woot")))
        )

        val owner2Id = ju.UUID.randomUUID()
        val res1 =
            sql.useBlocking(
                sql.withS(
                    sql.withTX(gm.promoteToOwner(owner2Id, owner1Id, gid).value)
                )
            )
        assert(res1 == Left(Groups.GroupError.MustBeOwner), res1)

        val res2 =
            sql.useBlocking(
                sql.withS(
                    sql.withTX(gm.promoteToOwner(owner1Id, owner2Id, gid).value)
                )
            )
        assert(res2 == Left(Groups.GroupError.TargetNotInGroup), res2)

        val res3 =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.addToGroup(owner2Id, gid).value))
            )
        assert(res3 == Right(()), res3)

        val res4 =
            sql.useBlocking(
                sql.withS(
                    sql.withTX(gm.promoteToOwner(owner1Id, owner2Id, gid).value)
                )
            )
        assert(res4 == Right(()), res4)

        val res5 =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.giveUpOwnership(owner1Id, gid).value))
            )
        assert(res5 == Right(()), res5)

        val res6 =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.giveUpOwnership(owner2Id, gid).value))
            )
        assert(res6 == Left(Groups.GroupError.GroupWouldHaveNoOwners), res6)
    }
    sql.test("basic subgroups") { implicit sql =>
        val gm = Groups.GroupManager()

        val ownerID = ju.UUID.randomUUID()
        val gid = sql.useBlocking(
            sql.withS(sql.withTX(gm.createGroup(ownerID, "Pavia")))
        )
        val res0 =
            sql.useBlocking(
                sql.withS(
                    sql.withTX(
                        gm.createRole(ownerID, gid, "Coast Guard Members").value
                    )
                )
            )
        assert(res0.isRight, res0)
        val rid = res0.getOrElse(???)

        val res1 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.createSubgroup(
                        ownerID,
                        gid,
                        "Coast Guard Infrastructure",
                    ).value
                )
            )
        )
        assert(res1.isRight, res1)
        val subgroupID = res1.getOrElse(???)

        val normalCitizen = ju.UUID.randomUUID()
        val res2 =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.addToGroup(normalCitizen, gid).value))
            )
        assert(res2.isRight, res2)

        val militaryCitizen = ju.UUID.randomUUID()
        val res3 = sql.useBlocking(
            sql.withS(sql.withTX(gm.addToGroup(militaryCitizen, gid).value))
        )
        assert(res3.isRight, res3)

        val res4 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.assignRole(ownerID, militaryCitizen, gid, rid, true)
                        .value
                )
            )
        )
        assert(res4.isRight, res4)

        val res5 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.setSubgroupRolePermissions(
                        ownerID,
                        gid,
                        subgroupID,
                        rid,
                        Map(
                            Permissions.Chests -> RuleMode.Allow,
                            Permissions.Doors -> RuleMode.Allow,
                            Permissions.Crops -> RuleMode.Allow,
                            Permissions.Build -> RuleMode.Allow,
                            Permissions.Entities -> RuleMode.Allow,
                        ),
                    ).value
                )
            )
        )
        assert(res5.isRight, res5)

        val res6 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.setSubgroupRolePermissions(
                        ownerID,
                        gid,
                        subgroupID,
                        everyoneUUID,
                        Map(
                            Permissions.Chests -> RuleMode.Deny,
                            Permissions.Doors -> RuleMode.Deny,
                            Permissions.Crops -> RuleMode.Deny,
                            Permissions.Build -> RuleMode.Deny,
                            Permissions.Entities -> RuleMode.Deny,
                        ),
                    ).value
                )
            )
        )
        assert(res6.isRight, res6)

        val res7 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(normalCitizen, gid, subgroupID, Permissions.Build)
                        .value
                )
            )
        )
        assert(res7 == Right(false), res7)

        val res8 = sql.useBlocking(
            sql.withS(
                sql.withTX(
                    gm.check(
                        militaryCitizen,
                        gid,
                        subgroupID,
                        Permissions.Build,
                    ).value
                )
            )
        )
        assert(res8 == Right(true), res8)
    }
