package BallCore.Sigils

import BallCore.Storage
import scalikejdbc._
import scalikejdbc.DBSession
import BallCore.Groups.GroupID
import BallCore.Groups.UserID
import java.util.UUID

class BanishmentManager()(using sql: Storage.SQLManager):
	sql.applyMigration(
		Storage.Migration(
			"Initial Banishment Manager",
			List(
				sql"""
				CREATE TABLE Banishments (
					UserID TEXT NOT NULL,
					GroupID TEXT NOT NULL,
					UNIQUE(UserID, GroupID),
					FOREIGN KEY (GroupID) REFERENCES GroupStates(ID) ON DELETE CASCADE
				);
				""",
			),
			List(
				sql"""
				DROP TABLE Banishments
				""",
			),
		)
	)
	private implicit val session: DBSession = sql.session

	def banishedUsers(from: GroupID): List[UserID] =
		sql"""
		SELECT UserID FROM Banishments WHERE GroupID = ${from};
		"""
		.map(rs => UUID.fromString(rs.string("UserID")))
		.list
		.apply()

	def isBanished(user: UserID, from: GroupID): Boolean =
		sql"""
		SELECT EXISTS (
			SELECT 1 FROM Banishments WHERE UserID = ${user} AND GroupID = ${from}
		);
		"""
		.map(rs => rs.boolean(0))
		.single
		.apply()
		.getOrElse(false)

	def banish(user: UserID, from: GroupID): Unit =
		sql"""
		INSERT INTO Banishments (
			UserID, GroupID
		) VALUES (
			${user}, ${from}
		);
		"""
		.update
		.apply()

	def unbanish(user: UserID, from: GroupID): Unit =
		sql"""
		DELETE FROM Banishments
			WHERE UserID = ${user}
			  AND GroupID = ${from};
		"""
		.update
		.apply()
