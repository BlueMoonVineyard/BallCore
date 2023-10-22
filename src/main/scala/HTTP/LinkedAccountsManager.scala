package BallCore.HTTP

import BallCore.Storage.SQLManager
import BallCore.Storage.Migration
import skunk.implicits._
import skunk.codec.all._
import scala.util.Random
import skunk.SqlState
import java.util.UUID

enum LinkedAccountError:
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

	def startLinkProcessFromDiscord(user: String): String =
		val code = generateLinkCode()

		sql.useBlocking(sql.commandIO(sql"""
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
		})

	def startLinkProcessFromMinecraft(user: UUID): String =
		val code = generateLinkCode()

		sql.useBlocking(sql.commandIO(sql"""
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
		})

	def finishLinkProcessFromDiscord(user: String): Either[LinkedAccountError, Unit] =
		sql.useBlocking(sql.queryOptionIO(sql"""
		SELECT MinecraftUUID FROM PendingDiscordLinkages WHERE LinkCode = $text
		""", uuid.opt, user)) match
			case None =>
				Left(LinkedAccountError.linkCodeDoesNotExist)
			case Some(None) =>
				Left(LinkedAccountError.linkCodeWasNotStartedFromMinecraft)
			case Some(Some(mcuuid)) =>
				sql.useBlocking(sql.txIO { tx =>
					sql.commandIO(sql"""
					DELETE FROM PendingDiscordLinkages WHERE MinecraftUUID = $uuid OR DiscordID = $text;
					""", (mcuuid, user)).flatMap { _ =>
					sql.commandIO(sql"""
					INSERT INTO DiscordLinkages (MinecraftUUID, DiscordID) VALUES ($uuid, $text);
					""", (mcuuid, user))
					}.map { _ =>
					Right(())
					}
				})

	def finishLinkProcessFromMinecraft(user: UUID): Either[LinkedAccountError, Unit] =
		sql.useBlocking(sql.queryOptionIO(sql"""
		SELECT DiscordID FROM PendingDiscordLinkages WHERE LinkCode = $uuid
		""", text.opt, user)) match
			case None =>
				Left(LinkedAccountError.linkCodeDoesNotExist)
			case Some(None) =>
				Left(LinkedAccountError.linkCodeWasNotStartedFromDiscord)
			case Some(Some(discordID)) =>
				sql.useBlocking(sql.txIO { tx =>
					sql.commandIO(sql"""
					DELETE FROM PendingDiscordLinkages WHERE MinecraftUUID = $uuid OR DiscordID = $text;
					""", (user, discordID)).flatMap { _ =>
					sql.commandIO(sql"""
					INSERT INTO DiscordLinkages (MinecraftUUID, DiscordID) VALUES ($uuid, $text);
					""", (user, discordID))
					}.map { _ =>
					Right(())
					}
				})
