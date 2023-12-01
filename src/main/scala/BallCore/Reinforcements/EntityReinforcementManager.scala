// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import BallCore.DataStructures.Clock
import BallCore.Groups
import BallCore.Storage.SQLManager

import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.math.*

class EntityReinforcementManager()(using
    esm: EntityStateManager,
    gsm: Groups.GroupManager,
    c: Clock,
    sql: SQLManager,
):
    private def hoist[B](
        either: Either[Groups.GroupError, B]
    ): Either[ReinforcementError, B] =
        either.left.map(BallCore.Reinforcements.ReinforcementGroupError.apply)

    def reinforce(
        as: Groups.UserID,
        group: Groups.GroupID,
        subgroup: Groups.SubgroupID,
        entity: UUID,
        kind: ReinforcementTypes,
    ): Either[ReinforcementError, Unit] =
        val state = esm.get(entity)
        state match
            case None =>
                hoist(
                    sql.useBlocking(
                        sql.withTX(
                            gsm
                                .checkE(
                                    as,
                                    group,
                                    subgroup,
                                    Groups.Permissions.AddReinforcements,
                                )
                                .value
                        )
                    )
                ).map { _ =>
                    esm.set(
                        entity,
                        ReinforcementState(
                            group,
                            subgroup,
                            as,
                            true,
                            false,
                            kind.hp,
                            kind,
                            c.now(),
                        ),
                    )
                }
            case Some(value) =>
                Left(AlreadyExists())

    def unreinforce(
        as: Groups.UserID,
        entity: UUID,
    ): Either[ReinforcementError, Unit] =
        esm.get(entity).filterNot(_.deleted) match
            case None => Left(DoesntExist())
            case Some(value) =>
                if as == value.owner then
                    esm.set(entity, value.copy(deleted = true))
                    Right(())
                else
                    hoist(
                        sql.useBlocking(
                            sql.withTX(
                                gsm
                                    .checkE(
                                        as,
                                        value.group,
                                        value.subgroup,
                                        Groups.Permissions.RemoveReinforcements,
                                    )
                                    .value
                            )
                        )
                    ).map { _ =>
                        esm.set(entity, value.copy(deleted = true))
                    }

    def damage(entity: UUID): Either[ReinforcementError, ReinforcementState] =
        esm.get(entity).filterNot(_.deleted) match
            case None => Left(DoesntExist())
            case Some(value) =>
                // TODO: factor in hearts + acclimation
                val hardness = 20
                val hoursPassed =
                    ChronoUnit.HOURS.between(value.placedAt, c.now()).toDouble
                val timeDamageMultiplier =
                    (hardness * exp(
                        (-1.0 / hardness) * hoursPassed
                    )) / 2.0 + 1.0
                val base = 1.0
                val newHealth =
                    (value.health.doubleValue() - (base * timeDamageMultiplier))
                        .intValue()
                val newValue =
                    value.copy(health = newHealth, deleted = newHealth <= 0)
                esm.set(entity, newValue)
                if newValue.deleted then Left(JustBroken(newValue))
                else Right(newValue)

    def getReinforcement(entity: UUID): Option[ReinforcementState] =
        esm.get(entity)
