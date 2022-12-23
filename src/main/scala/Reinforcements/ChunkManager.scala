// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Groups
import java.{util => ju}
import org.bukkit.NamespacedKey

type WorldID = NamespacedKey

sealed trait ChunkError
case class ChunkGroupError(error: Groups.GroupError) extends ChunkError

/** The ChunkManager implements all of the logic of reinforcement data */
class ChunkManager()(using csm: ChunkStateManager, gsm: Groups.GroupManager):
    private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
        (x / 16, z / 16, x % 16, z % 16)
    private def fromOffsets(chunkX: Int, chunkZ: Int, offsetX: Int, offsetZ: Int): (Int, Int) =
        ((chunkX*16) + offsetX, (chunkZ*16) + offsetZ)
    private def hoist[B](either: Either[Groups.GroupError, B]): Either[ChunkError, B] =
        either.left.map(ChunkGroupError(_))

    def reinforce(as: Groups.UserID, group: Groups.UserID, x: Int, y: Int, z: Int, world: WorldID): Either[ChunkError, Unit] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey) match
            // None | Some(value) if value.deleted
            case None =>
                // TODO: check that user has perms for this
                hoist(gsm.check(as, group, Groups.Permissions.AddReinforcements)).map { _ =>
                    state.blocks(bkey) = BlockState(group, as, true, false)
                }
            case Some(value) => ???
    def unreinforce(as: Groups.UserID, group: Groups.UserID, x: Int, y: Int, z: Int, world: WorldID): Either[ChunkError, Unit] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey) match
            // None | Some(value) if value.deleted
            case None => ???
            case Some(value) =>
                if as == value.owner then
                    Right(())
                else
                    hoist(gsm.check(as, group, Groups.Permissions.RemoveReinforcements)).map { _ =>
                        state.blocks(bkey) = value.copy(deleted = true)
                    }
    def getReinforcement(x: Int, y: Int, z: Int, world: WorldID): Option[BlockState] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey)
