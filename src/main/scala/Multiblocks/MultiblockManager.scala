package BallCore.Multiblocks

import BallCore.Storage
import scalikejdbc._
import scalikejdbc.SQL
import scalikejdbc.NoExtractor

class MultiblockManager(using sql: Storage.SQLManager):
    sql.applyMigration(
        Storage.Migration(
            "Initial MultiblockManager",
            List(
                sql"""
                CREATE TABLE BlocksToMultiblocks (
                    BlockX INTEGER NOT NULL,
                    BlockY INTEGER NOT NULL,
                    BlockZ INTEGER NOT NULL,
                    World TEXT NOT NULL,
                    Multiblock TEXT NOT NULL,
                    FOREIGN KEY (Multiblock) REFERENCES Multiblocks(ID) ON DELETE CASCADE
                );
                """,
                sql"""
                CREATE TABLE Multiblocks (
                    ID TEXT NOT NULL,
                    Class TEXT NOT NULL,
                    Data BLOB NOT NULL,
                    UNIQUE(ID)
                );
                """,
            ),
            List(
                sql"""
                DROP TABLE BlocksToMultiblocks;
                """,
                sql"""
                DROP TABLE Multiblocks;
                """,
            ),
        )
    )

    private implicit val session: DBSession = sql.session
