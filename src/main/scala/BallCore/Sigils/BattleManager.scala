package BallCore.Sigils

import BallCore.Storage
import skunk.implicits._

class BattleManager(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial Battle Manager",
            List(
                sql"""
				CREATE TABLE Battles(
					BattleID UUID PRIMARY KEY,
					OffensiveGroup UUID NOT NULL,
					DefensiveGroup UUID NOT NULL,
					UNIQUE(OffensiveGroup, DefensiveGroup)
				);
				""".command
            ),
            List(),
        )
    )
