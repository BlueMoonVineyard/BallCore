package BallCore.Sigils

import BallCore.Storage
import scalikejdbc._
import scalikejdbc.DBSession
import BallCore.Hearts.HeartNetworkID
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
					HeartNetworkID TEXT NOT NULL,
					UNIQUE(UserID, HeartNetworkID),
					FOREIGN KEY (HeartNetworkID) REFERENCES HeartNetworks(ID) ON DELETE CASCADE
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

	def banishedUsers(from: HeartNetworkID): List[UserID] =
		sql"""
		SELECT UserID FROM Banishments WHERE HeartNetworkID = ${from};
		"""
		.map(rs => UUID.fromString(rs.string("UserID")))
		.list
		.apply()

	def isBanished(user: UserID, from: HeartNetworkID): Boolean =
		sql"""
		SELECT EXISTS (
			SELECT 1 FROM Banishments WHERE UserID = ${user} AND HeartNetworkID = ${from}
		);
		"""
		.map(rs => rs.boolean(0))
		.single
		.apply()
		.getOrElse(false)

	def banish(user: UserID, from: HeartNetworkID): Unit =
		sql"""
		INSERT INTO Banishments (
			UserID, HeartNetworkID
		) VALUES (
			${user}, ${from}
		);
		"""
		.update
		.apply()

	def unbanish(user: UserID, from: HeartNetworkID): Unit =
		sql"""
		DELETE FROM Banishments
			WHERE UserID = ${user}
			  AND HeartNetworkID = ${from};
		"""
		.update
		.apply()
