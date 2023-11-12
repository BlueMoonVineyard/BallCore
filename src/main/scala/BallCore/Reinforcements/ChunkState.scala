// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Groups
import io.circe.*
import io.circe.generic.semiauto.*

import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.Map

/** This keys the reinforcement state for a given block in a chunkstate */
case class BlockKey(
    offsetX: Int,
    offsetZ: Int,
    y: Int
)

/** This holds the reinforcement state for a given block in a chunkstate */
case class ReinforcementState(
    group: Groups.GroupID,
    subgroup: Groups.SubgroupID,
    owner: Groups.UserID,
    dirty: Boolean,
    deleted: Boolean,
    health: Int,
    kind: ReinforcementTypes,
    placedAt: OffsetDateTime
)

/** This keys ChunkStates in the cache */
case class ChunkKey(
    chunkX: Int,
    chunkZ: Int,
    world: UUID
)

/** This holds reinforcement information for a single chunk (the Map is mutable
  * for performance reasons)
  */
case class ChunkState(
    // chunkX: Int,
    // chunkZ: Int,
    // world: ju.UUID,
    blocks: mutable.Map[BlockKey, ReinforcementState]
)
