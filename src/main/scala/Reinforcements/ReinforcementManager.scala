// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Groups
import java.{util => ju}
import org.bukkit.NamespacedKey

type WorldID = NamespacedKey

sealed trait ReinforcementError
case class ReinforcementGroupError(error: Groups.GroupError) extends ReinforcementError
case class AlreadyExists() extends ReinforcementError
case class DoesntExist() extends ReinforcementError

/** The ReinforcementManager implements all of the logic of reinforcement data */
class ReinforcementManager()(using csm: ChunkStateManager, gsm: Groups.GroupManager):
    private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
        (x / 16, z / 16, x % 16, z % 16)
    private def fromOffsets(chunkX: Int, chunkZ: Int, offsetX: Int, offsetZ: Int): (Int, Int) =
        ((chunkX*16) + offsetX, (chunkZ*16) + offsetZ)
    private def hoist[B](either: Either[Groups.GroupError, B]): Either[ReinforcementError, B] =
        either.left.map(ReinforcementGroupError(_))

    def reinforce(as: Groups.UserID, group: Groups.UserID, x: Int, y: Int, z: Int, world: WorldID, health: Int): Either[ReinforcementError, Unit] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey).filterNot(_.deleted) match
            case None =>
                hoist(gsm.checkE(as, group, Groups.Permissions.AddReinforcements)).map { _ =>
                    state.blocks(bkey) = BlockState(group, as, true, false, health, health, ju.Date())
                }
            case Some(value) =>
                Left(AlreadyExists())
    def unreinforce(as: Groups.UserID, group: Groups.UserID, x: Int, y: Int, z: Int, world: WorldID): Either[ReinforcementError, Unit] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey).filterNot(_.deleted) match
            case None => Left(DoesntExist())
            case Some(value) =>
                if as == value.owner then
                    Right(())
                else
                    hoist(gsm.checkE(as, group, Groups.Permissions.RemoveReinforcements)).map { _ =>
                        state.blocks(bkey) = value.copy(deleted = true)
                    }
    def getReinforcement(x: Int, y: Int, z: Int, world: WorldID): Option[BlockState] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey)
