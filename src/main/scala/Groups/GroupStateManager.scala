// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.Storage
import BallCore.DataStructures.LRUCache
import java.{util => ju}

/** The GroupStateManager is responsible for loading and saving GroupStates to disk as appropriate */
class GroupStateManager()(using kvs: Storage.KeyVal):
    val cache = LRUCache[GroupID, GroupState](1000, evict)

    private def evict(group: GroupID, state: GroupState): Unit =
        kvs.set("GroupStates", group.toString(), state)

    private def save(group: GroupID): Unit =
        if !cache.contains(group) then
            return
        kvs.set("GroupStates", group.toString(), cache(group))

    private def load(group: GroupID) =
        kvs.get[GroupState]("GroupStates", group.toString()).map(g => cache(group) = g)

    def remove(group: GroupID): Unit =
        cache.remove(group)
        kvs.remove("GroupStates", group.toString())

    def set(group: GroupID, state: GroupState): Unit =
        cache(group) = state
        save(group)

    def get(group: GroupID): Option[GroupState] =
        if !cache.contains(group) then
            load(group)
        cache.get(group)

    def check(perm: Permissions, user: UserID, group: GroupID): Boolean =
        if !cache.contains(group) then
            load(group)
        cache(group).check(perm, user)