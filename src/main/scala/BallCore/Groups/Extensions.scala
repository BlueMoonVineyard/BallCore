// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import cats.Monad
import cats.data.EitherT

object Extensions:
  extension[F[_] : Monad, A, B] (e: EitherT[F, A, B])
    def guard(onFalse: A)(cond: B => Boolean): EitherT[F, A, B] =
      e.flatMap { data =>
        if cond(data) then EitherT.fromEither(Right(data))
        else EitherT.fromEither(Left(onFalse))
      }
  extension[F[_] : Monad] (e: EitherT[F, GroupError, GroupState])
    def guardRoleAboveYours(
                             as: UserID,
                             role: RoleID
                           ): EitherT[F, GroupError, GroupState] =
      e.flatMap { state =>
        if state.owners.contains(as) then EitherT.fromEither(Right(state))
        else
          val myRoles = state.users(as)
          val highestRoleIdx = state.roles.indexOf(
            state.roles.filter(r => myRoles.contains(r.id)).head
          )
          val targetRoleIdx = state.roles.indexWhere(_.id == role)
          if targetRoleIdx <= highestRoleIdx then
            EitherT.fromEither(Left(GroupError.RoleAboveYours))
          else EitherT.fromEither(Right(state))
      }
