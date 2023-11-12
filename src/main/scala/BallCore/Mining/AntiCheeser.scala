// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Mining

import BallCore.Storage.{Migration, SQLManager}
import cats.effect.IO
import cats.syntax.all.*
import org.bukkit.block.Block
import skunk.Session
import skunk.codec.all.*
import skunk.data.Completion
import skunk.implicits.*

/*

# Anti-Cheesing

Anti-cheesing slices up chunks into y layers, initialising each layer's "health" with the number of non-air blocks
in that layer when it is first accessed.

Every chance to get something from breaking decreases the health by one.

 */

// methods must only be called within the scheduler for the target blocks
class AntiCheeser()(using sql: SQLManager):
    sql.applyMigration(
        Migration(
            "Initial Mining AntiCheeser",
            List(
                sql"""
                CREATE TABLE MiningAntiCheeser (
                    ChunkX BIGINT NOT NULL,
                    ChunkZ BIGINT NOT NULL,
                    Y BIGINT NOT NULL,
                    World UUID NOT NULL,
                    Health INTEGER NOT NULL,
                    UNIQUE(ChunkX, ChunkZ, Y, World)
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE MiningAntiCheeser;
                """.command
            ),
        )
    )

    private def countEligibleBlocks(b: Block): Int =
        val cx0 = b.getChunk.getX << 4
        val cz0 = b.getChunk.getZ << 4
        val types = for {
            dx <- 0 to 15
            dz <- 0 to 15
        } yield b.getWorld.getBlockAt(cx0 + dx, b.getY, cz0 + dz).getType
        types.count(kind => Mining.stoneBlocks.contains(kind))

    def blockBrokenPartA(b: Block)(using Session[IO]): IO[Option[Int]] =
        val cx = b.getChunk.getX
        val cz = b.getChunk.getZ
        val y = b.getY
        val world = b.getWorld.getUID
        sql.queryOptionIO(
            sql"""
        SELECT Health FROM MiningAntiCheeser WHERE ChunkX = $int8 AND ChunkZ = $int8 AND Y = $int8 AND World = $uuid
        """,
            int4,
            (cx, cz, y, world),
        )

    def blockBrokenPartB(b: Block, it: Option[Int]): Int =
        it.getOrElse(countEligibleBlocks(b))

    def blockBrokenPartC(b: Block, it: Int)(using Session[IO]): IO[Boolean] =
        val cx = b.getChunk.getX
        val cz = b.getChunk.getZ
        val y = b.getY
        val world = b.getWorld.getUID
        val health = it - 1

        for {
            _ <- sql.commandIO(
                sql"""
            INSERT INTO MiningAntiCheeser (
                Health, ChunkX, ChunkZ, Y, World
            ) VALUES (
                $int4, $int8, $int8, $int8, $uuid
            ) ON CONFLICT (ChunkX, ChunkZ, Y, World) DO UPDATE SET Health = EXCLUDED.Health;
            """,
                (health.max(0), cx, cz, y, world),
            )
        } yield health >= 0
