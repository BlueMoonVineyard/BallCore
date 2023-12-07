// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.Lexorank

class LexorankSuite extends munit.FunSuite:
    val tests = List(
        ("new digit", "aaaa", "aaab"),
        ("mid value", "aaaa", "aaac"),
        ("new digit mid", "az", "b"),
        ("g and p", "g", "p"),
    )
    tests.foreach { case (title, prev, next) =>
        test(title) {
            val mid = Lexorank.rank(prev, next)
            assert(prev < mid, (prev, mid, next))
            assert(mid < next, (prev, mid, next))
        }
    }
