package BallCore.HTTP

import BallCore.Storage.SQLManager
import BallCore.Storage.Migration
import skunk.implicits._

class LinkedAccountsManager()(using sql: SQLManager):
	sql.applyMigration(
		Migration(
			"Initial LinkedAccountsManager",
			List(
				sql"""
				CREATE TABLE DiscordLinkages (
					MinecraftUUID TEXT NOT NULL,
					DiscordID TEXT NOT NULL,
					UNIQUE(MinecraftUUID, DiscordID)
				);
				""".command,
				sql"""
				CREATE TABLE PendingDiscordLinkages (
					MinecraftUUID TEXT,
					DiscordID TEXT,
					LinkCode TEXT,
					UNIQUE(MinecraftUUID, DiscordID)
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
