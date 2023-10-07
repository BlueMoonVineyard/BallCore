// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.Storage
import java.util.UUID
import skunk.implicits._
import skunk.codec.all._
import cats.effect.unsafe.IORuntime
import cats.effect.IO
import skunk.Session
import skunk.data.Completion
import cats.data.EitherT

class InviteManager()(using sql: Storage.SQLManager, gm: GroupManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial Invite Manager",
            List(
                sql"""
                CREATE TABLE Invites(
                    Creator UUID NOT NULL,
                    Invitee UUID NOT NULL,
                    GroupID UUID NOT NULL,
                    UNIQUE(Invitee, GroupID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE Invites;
                """.command
            )
        )
    )
    given runtime: IORuntime = sql.runtime

    def inviteToGroup(from: UUID, to: UUID, group: GroupID)(using s: Session[IO]): IO[Completion] =
        sql.commandIO(sql"""
        INSERT INTO Invites (
            Creator, Invitee, GroupID
        ) VALUES (
            $uuid, $uuid, $uuid
        )
        """, (from, to, group))

    def getInvitesFor(player: UUID)(using s: Session[IO]): IO[List[(UUID, GroupID)]] =
        sql.queryListIO(sql"""
        SELECT (Creator, GroupID) FROM Invites WHERE Invitee = $uuid
        """, (uuid *: uuid), player)

    def deleteInvite(invitee: UserID, group: GroupID)(using s: Session[IO]): IO[Completion] =
        sql.commandIO(sql"""
        DELETE FROM Invites WHERE Invitee = $uuid AND GroupID = $uuid
        """, (invitee, group))

    def acceptInvite(invitee: UserID, group: GroupID)(using s: Session[IO]): IO[Either[GroupError, Unit]] =
        s.transaction.use { tx =>
            EitherT.right(deleteInvite(invitee, group)).flatMap { _ =>
                gm.addToGroup(invitee, group)
            }.value
        }
