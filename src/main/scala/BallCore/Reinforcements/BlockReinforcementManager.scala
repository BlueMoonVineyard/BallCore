// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.DataStructures.Clock
import BallCore.Groups
import BallCore.Storage.SQLManager

import java.time.temporal.ChronoUnit
import java.util as ju
import java.util.UUID
import scala.math.*

type WorldID = UUID

sealed trait ReinforcementError

case class ReinforcementGroupError(error: Groups.GroupError)
    extends ReinforcementError

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

/** The ReinforcementManager implements all of the logic of reinforcement data
  */
class BlockReinforcementManager()(using
    csm: ChunkStateManager,
    gsm: Groups.GroupManager,
    c: Clock,
    sql: SQLManager
):
  private def toOffsets(x: Int, z: Int): (Int, Int, Int, Int) =
    (x / 16, z / 16, x % 16, z % 16)

  private def hoist[B](
      either: Either[Groups.GroupError, B]
  ): Either[ReinforcementError, B] =
    either.left.map(BallCore.Reinforcements.ReinforcementGroupError.apply)

  def reinforce(
      as: Groups.UserID,
      group: Groups.UserID,
      subgroup: Groups.SubgroupID,
      x: Int,
      y: Int,
      z: Int,
      world: WorldID,
      kind: ReinforcementTypes
  ): Either[ReinforcementError, Unit] =
    val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
    val state = csm.get(ChunkKey(chunkX, chunkZ, world))
    val bkey = BlockKey(offsetX, offsetZ, y)
    state.blocks.get(bkey).filterNot(_.deleted) match
      case None =>
        hoist(
          sql.useBlocking(
            gsm
              .checkE(as, group, subgroup, Groups.Permissions.AddReinforcements)
              .value
          )
        ).map { _ =>
          state.blocks(bkey) = ReinforcementState(
            group,
            subgroup,
            as,
            true,
            false,
            kind.hp,
            kind,
            c.now()
          )
        }
      case Some(value) =>
        Left(AlreadyExists())

  def unreinforce(
      as: Groups.UserID,
      x: Int,
      y: Int,
      z: Int,
      world: WorldID
  ): Either[ReinforcementError, Unit] =
    val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
    val state = csm.get(ChunkKey(chunkX, chunkZ, world))
    val bkey = BlockKey(offsetX, offsetZ, y)
    state.blocks.get(bkey).filterNot(_.deleted) match
      case None => Left(DoesntExist())
      case Some(value) =>
        if as == value.owner then
          state.blocks(bkey) = value.copy(deleted = true)
          Right(())
        else
          hoist(
            sql.useBlocking(
              gsm
                .checkE(
                  as,
                  value.group,
                  value.subgroup,
                  Groups.Permissions.RemoveReinforcements
                )
                .value
            )
          ).map { _ =>
            state.blocks(bkey) = value.copy(deleted = true)
          }

  def break(
      x: Int,
      y: Int,
      z: Int,
      hardness: Double,
      world: WorldID
  ): Either[ReinforcementError, ReinforcementState] =
    val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
    val state = csm.get(ChunkKey(chunkX, chunkZ, world))
    val bkey = BlockKey(offsetX, offsetZ, y)
    state.blocks.get(bkey).filterNot(_.deleted) match
      case None        => Left(DoesntExist())
      case Some(value) =>
        // TODO: factor in hearts + acclimation
        val hoursPassed =
          ChronoUnit.HOURS.between(value.placedAt, c.now()).toDouble
        val timeDamageMultiplier =
          (hardness * exp((-1.0 / hardness) * hoursPassed)) / 2.0 + 1.0
        val base = 1.0
        val newHealth =
          (value.health.doubleValue() - (base * timeDamageMultiplier))
            .intValue()
        val newValue = value.copy(health = newHealth, deleted = newHealth <= 0)
        state.blocks(bkey) = newValue
        if newValue.deleted then Left(JustBroken(newValue))
        else Right(newValue)

  def getReinforcement(
      x: Int,
      y: Int,
      z: Int,
      world: WorldID
  ): Option[ReinforcementState] =
    val (chunkX, chunkZ, offsetX, offsetZ) = toOffsets(x, z)
    val state = csm.get(ChunkKey(chunkX, chunkZ, world))
    val bkey = BlockKey(offsetX, offsetZ, y)
    state.blocks.get(bkey)
