// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

object Extensions:
    extension [A, B](e: Either[A, B])
        def guard(onFalse: A)(cond: B => Boolean): Either[A, B] =
            e.flatMap { data =>
                if cond(data) then
                    Right(data)
                else
                    Left(onFalse)
            }
    extension (e: Either[GroupError, GroupState])
        def guardRoleAboveYours(as: UserID, role: RoleID): Either[GroupError, GroupState] =
            e.flatMap { state =>
                if state.owners.contains(as) then
                    Right(state)
                else
                    val myRoles = state.users(as)
                    val highestRoleIdx = state.roles.indexOf(state.roles.filter(r => myRoles.contains(r.id))(0))
                    val targetRoleIdx = state.roles.indexWhere(_.id == role)
                    if targetRoleIdx <= highestRoleIdx then
                        Left(GroupError.RoleAboveYours)
                    else
                        Right(state)
            }
