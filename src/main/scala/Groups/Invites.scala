package BallCore.Groups

import BallCore.Storage
import scalikejdbc._
import java.util.UUID

class InviteManager()(using sql: Storage.SQLManager, gm: GroupManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial Invite Manager",
            List(
                sql"""
                CREATE TABLE Invites(
                    Creator TEXT NOT NULL,
                    Invitee TEXT NOT NULL,
                    GroupID TEXT NOT NULL,
                    UNIQUE(Invitee, GroupID),
                    FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE
                );
                """
            ),
            List(
                sql"""
                DROP TABLE Invites;
                """
            )
        )
    )
    implicit val session: DBSession = sql.session

    def inviteToGroup(from: UUID, to: UUID, group: GroupID): Unit =
        sql"""
        INSERT INTO Invites (
            Creator, Invitee, GroupID
        ) VALUES (
            ${from}, ${to}, ${group}
        )
        """
        .update
        .apply()

    def getInvitesFor(player: UUID): List[(UUID, GroupID)] =
        sql"""
        SELECT * FROM Invites WHERE Invitee = ${player};
        """
        .map(rs => (uuid(rs.string("Creator")), uuid(rs.string("GroupID"))))
        .list
        .apply()

    def deleteInvite(invitee: UserID, group: GroupID): Unit =
        sql"""
        DELETE FROM Invites WHERE Invitee = ${invitee} AND GroupID = ${group}
        """
        .update
        .apply()

    def acceptInvite(invitee: UserID, group: GroupID): Either[GroupError, Unit] =
        sql.localTx { implicit session =>
            deleteInvite(invitee, group)
            gm.addToGroup(invitee, group)
        }
