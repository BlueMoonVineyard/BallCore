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
            
