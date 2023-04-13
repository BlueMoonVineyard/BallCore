// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import java.time.Instant
import BallCore.Groups
import scala.collection.mutable.Map
import io.circe._, io.circe.generic.semiauto._

/** This keys the reinforcement state for a given block in a chunkstate */
case class BlockKey(
    offsetX: Int,
    offsetZ: Int,
    y: Int,
)

/** This holds the reinforcement state for a given block in a chunkstate */
case class ReinforcementState(
    group: Groups.GroupID,
    owner: Groups.UserID,
    dirty: Boolean,
    deleted: Boolean,
    health: Int,
    kind: ReinforcementTypes,
    placedAt: Instant,
)

/** This keys ChunkStates in the cache */
case class ChunkKey(
    chunkX: Int,
    chunkZ: Int,
    world: String,
)

/** This holds reinforcement information for a single chunk (the Map is mutable for performance reasons) */
case class ChunkState(
    // chunkX: Int,
    // chunkZ: Int,
    // world: ju.UUID,
    blocks: Map[BlockKey, ReinforcementState],
)
