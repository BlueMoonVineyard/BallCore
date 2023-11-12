// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import BallCore.DataStructures.Lexorank

class LexorankSuite extends munit.FunSuite:
    test("basic lexorank") {
        val prev = "a"
        val next = "d"
        val mid = Lexorank.rank(prev, next)
        assert(prev < mid, (prev, mid, next))
        assert(mid < next, (prev, mid, next))
    }
    test("complicated lexorank") {
        val prev = "alksdjlkg"
        val next = "alkszjlkg"
        val mid = Lexorank.rank(prev, next)
        assert(prev < mid, (prev, mid, next))
        assert(mid < next, (prev, mid, next))
    }
