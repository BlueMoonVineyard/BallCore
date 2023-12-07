// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.DataStructures

object Lexorank:
    private val minChar = '0'
    private val maxChar = 'z'

    private def mid(l: Char, r: Char): Char =
        ((l + r) / 2).toChar

    private def getChar(str: String, i: Int, default: Char): Char =
        if i >= str.length then default
        else str.charAt(i)

    def rank(prev: String, next: String): String =
        val prev_ = if prev == "" then minChar.toString else prev
        val next_ = if next == "" then maxChar.toString else next

        var rank = ""
        var i = 0
        var done = false

        while (!done) {
            val prevChar = getChar(prev_, i, minChar)
            val nextChar = getChar(next_, i, maxChar)
            val midChar = mid(prevChar, nextChar)

            if prevChar == nextChar then
                rank = s"$rank$prevChar"
                i = i + 1
            else if midChar == prevChar || midChar == nextChar then
                rank = s"$rank$prevChar"
                i = i + 1
            else
                rank = s"$rank$midChar"
                done = true
        }

        if rank < next_ then rank
        else prev_
