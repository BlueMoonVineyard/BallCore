// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.Groups
import java.{util => ju}
import org.bukkit.NamespacedKey
import java.time.temporal.ChronoUnit
import scala.math._
import BallCore.DataStructures.Clock
import java.util.UUID

type WorldID = UUID

sealed trait ReinforcementError
case class ReinforcementGroupError(error: Groups.GroupError) extends ReinforcementError
case class JustBroken(bs: ReinforcementState) extends ReinforcementError
case class AlreadyExists() extends ReinforcementError
case class DoesntExist() extends ReinforcementError

def explain(err: ReinforcementError): String =
    err match
        case ReinforcementGroupError(error) =>
            error.explain()
        case AlreadyExists() =>
            "A reinforcement already exists there"
        case DoesntExist() =>
            "A reinforcement doesn't exist there"
        case JustBroken(_) =>
            "A reinforcement was just broken"

/** The ReinforcementManager implements all of the logic of reinforcement data */
class BlockReinforcementManager()(using csm: ChunkStateManager, gsm: Groups.GroupManager, c: Clock):
    private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
        (x / 16, z / 16, x % 16, z % 16)
    private def fromOffsets(chunkX: Int, chunkZ: Int, offsetX: Int, offsetZ: Int): (Int, Int) =
        ((chunkX*16) + offsetX, (chunkZ*16) + offsetZ)
    private def hoist[B](either: Either[Groups.GroupError, B]): Either[ReinforcementError, B] =
        either.left.map(ReinforcementGroupError(_))

    def reinforce(as: Groups.UserID, group: Groups.UserID, x: Int, y: Int, z: Int, world: WorldID, kind: ReinforcementTypes): Either[ReinforcementError, Unit] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey).filterNot(_.deleted) match
            case None =>
                hoist(gsm.checkE(as, group, Groups.Permissions.AddReinforcements)).map { _ =>
                    state.blocks(bkey) = ReinforcementState(group, as, true, false, kind.hp, kind, c.now())
                }
            case Some(value) =>
                Left(AlreadyExists())
    def unreinforce(as: Groups.UserID, x: Int, y: Int, z: Int, world: WorldID): Either[ReinforcementError, Unit] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey).filterNot(_.deleted) match
            case None => Left(DoesntExist())
            case Some(value) =>
                if as == value.owner then
                    state.blocks(bkey) = value.copy(deleted = true)
                    Right(())
                else
                    hoist(gsm.checkE(as, value.group, Groups.Permissions.RemoveReinforcements)).map { _ =>
                        state.blocks(bkey) = value.copy(deleted = true)
                    }
    def break(x: Int, y: Int, z: Int, hardness: Double, world: WorldID): Either[ReinforcementError, ReinforcementState] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey).filterNot(_.deleted) match
            case None => Left(DoesntExist())
            case Some(value) =>
                // TODO: factor in hearts + acclimation
                val hoursPassed = ChronoUnit.HOURS.between(value.placedAt, c.now()).toDouble
                val timeDamageMultiplier = (hardness * exp((-1.0/hardness) * hoursPassed))/2.0 + 1.0
                val base = 1.0
                val newHealth = (value.health.doubleValue() - (base * timeDamageMultiplier)).intValue()
                val newValue = value.copy(health = newHealth, deleted = newHealth <= 0)
                state.blocks(bkey) = newValue
                if newValue.deleted then
                    Left(JustBroken(newValue))
                else
                    Right(newValue)
    def getReinforcement(x: Int, y: Int, z: Int, world: WorldID): Option[ReinforcementState] =
        val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
        val state = csm.get(ChunkKey(chunkX, chunkZ, world.toString()))
        val bkey = BlockKey(offsetX, offsetZ, y)
        state.blocks.get(bkey)
