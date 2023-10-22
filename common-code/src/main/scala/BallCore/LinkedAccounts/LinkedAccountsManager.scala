package BallCore.LinkedAccounts

import BallCore.Storage.SQLManager
import BallCore.Storage.Migration
import skunk.implicits._
import skunk.codec.all._
import scala.util.Random
import skunk.SqlState
import java.util.UUID
import cats.effect.IO
import skunk.Session

enum LinkedAccountError:
	case accountAlreadyLinked
	case linkCodeDoesNotExist
	case linkCodeWasNotStartedFromMinecraft
	case linkCodeWasNotStartedFromDiscord

class LinkedAccountsManager()(using sql: SQLManager):
	sql.applyMigration(
		Migration(
			"Initial LinkedAccountsManager",
			List(
				sql"""
				CREATE TABLE DiscordLinkages (
					MinecraftUUID UUID NOT NULL,
					DiscordID TEXT NOT NULL,
					UNIQUE(MinecraftUUID, DiscordID)
				);
				""".command,
				sql"""
				CREATE TABLE PendingDiscordLinkages (
					MinecraftUUID UUID UNIQUE,
					DiscordID TEXT UNIQUE,
					LinkCode TEXT NOT NULL
				);
				""".command
			),
			List(
				sql"""
				DROP TABLE PendingDiscordLinkages;
				""".command,
				sql"""
				DROP TABLE DiscordLinkages;
				""".command
			),
		)
	)

	def generateLinkCode(): String =
		Random.alphanumeric.take(6).mkString

	def isAlreadyLinked(discordID: String): Boolean =
		sql.useBlocking(sql.queryUniqueIO(sql"""
		SELECT EXISTS(SELECT 1 FROM DiscordLinkages WHERE DiscordID = $text);
		""", bool, discordID))

	def isAlreadyLinked(mcuuid: UUID): Boolean =
		sql.useBlocking(sql.queryUniqueIO(sql"""
		SELECT EXISTS(SELECT 1 FROM DiscordLinkages WHERE MinecraftUUID = $uuid);
		""", bool, mcuuid))

	def startLinkProcessFromDiscord(user: String): Either[LinkedAccountError, String] =
		val code = generateLinkCode()

		if isAlreadyLinked(user) then
			return Left(LinkedAccountError.accountAlreadyLinked)

		Right(sql.useBlocking(sql.commandIO(sql"""
		INSERT INTO PendingDiscordLinkages (
			DiscordID, LinkCode
		) VALUES (
			$text, $text
		);
		""", (user, code)).map(_ => code).recoverWith {
			case SqlState.UniqueViolation(_) =>
				sql.queryUniqueIO(sql"""
				SELECT LinkCode FROM PendingDiscordLinkages WHERE DiscordID = $text
				""", text, user)
		}))

	def startLinkProcessFromMinecraft(user: UUID): Either[LinkedAccountError, String] =
		val code = generateLinkCode()

		if isAlreadyLinked(user) then
			return Left(LinkedAccountError.accountAlreadyLinked)

		Right(sql.useBlocking(sql.commandIO(sql"""
		INSERT INTO PendingDiscordLinkages (
			MinecraftUUID, LinkCode
		) VALUES (
			$uuid, $text
		);
		""", (user, code)).map(_ => code).recoverWith {
			case SqlState.UniqueViolation(_) =>
				sql.queryUniqueIO(sql"""
				SELECT LinkCode FROM PendingDiscordLinkages WHERE MinecraftUUID = $uuid
				""", text, user)
		}))

	private def insertLinkage(discordID: String, mcuuid: UUID)(using Session[IO]): IO[Either[LinkedAccountError, Unit]] =
		sql.txIO { tx =>
			sql.commandIO(sql"""
			DELETE FROM PendingDiscordLinkages WHERE MinecraftUUID = $uuid OR DiscordID = $text;
			""", (mcuuid, discordID)).flatMap { _ =>
			sql.commandIO(sql"""
			INSERT INTO DiscordLinkages (MinecraftUUID, DiscordID) VALUES ($uuid, $text);
			""", (mcuuid, discordID))
			}.recoverWith {
				case SqlState.UniqueViolation(_) => IO.pure(Left(LinkedAccountError.accountAlreadyLinked))
			}.map { _ =>
			Right(())
			}
		}

	def finishLinkProcessFromDiscord(linkCode: String, discordID: String): Either[LinkedAccountError, Unit] =
		sql.useBlocking(sql.queryOptionIO(sql"""
		SELECT MinecraftUUID FROM PendingDiscordLinkages WHERE LinkCode = $text
		""", uuid.opt, linkCode)) match
			case None =>
				Left(LinkedAccountError.linkCodeDoesNotExist)
			case Some(None) =>
				Left(LinkedAccountError.linkCodeWasNotStartedFromMinecraft)
			case Some(Some(mcuuid)) =>
				sql.useBlocking(insertLinkage(discordID, mcuuid))

	def finishLinkProcessFromMinecraft(linkCode: String, mcuuid: UUID): Either[LinkedAccountError, Unit] =
		sql.useBlocking(sql.queryOptionIO(sql"""
		SELECT DiscordID FROM PendingDiscordLinkages WHERE LinkCode = $text
		""", text.opt, linkCode)) match
			case None =>
				Left(LinkedAccountError.linkCodeDoesNotExist)
			case Some(None) =>
				Left(LinkedAccountError.linkCodeWasNotStartedFromDiscord)
			case Some(Some(discordID)) =>
				sql.useBlocking(insertLinkage(discordID, mcuuid))
