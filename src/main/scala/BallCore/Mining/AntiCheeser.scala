// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Mining

import BallCore.Storage.{Migration, SQLManager}
import cats.effect.IO
import org.bukkit.block.Block
import skunk.Session
import skunk.codec.all.*
import skunk.data.Completion
import skunk.implicits.*
import scala.collection.concurrent.TrieMap
import cats.effect.kernel.Resource
import java.util.UUID

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

    private val cache = TrieMap[(Int, Int, Int, UUID), Int]()
    private def get(b: Block)(using
        rsrc: Resource[IO, Session[IO]]
    ): IO[Option[Int]] =
        val cx = b.getChunk.getX
        val cz = b.getChunk.getZ
        val y = b.getY
        val world = b.getWorld.getUID
        IO { cache.get((cx, cz, y, world)) }
            .flatMap { opt =>
                opt match
                    case None =>
                        rsrc.use { implicit session =>
                            sql.queryOptionIO(
                                sql"""
                            SELECT Health FROM MiningAntiCheeser WHERE ChunkX = $int8 AND ChunkZ = $int8 AND Y = $int8 AND World = $uuid
                            """,
                                int4,
                                (cx, cz, y, world),
                            )
                        }
                    case Some(_) =>
                        IO.pure(opt)
            }
    private def set(b: Block, h: Int)(using
        rsrc: Resource[IO, Session[IO]]
    ): IO[Unit] =
        val cx = b.getChunk.getX
        val cz = b.getChunk.getZ
        val y = b.getY
        val world = b.getWorld.getUID

        for {
            _ <- IO { cache.update((cx, cz, y, world), h) }
            _ <- rsrc.use { implicit session =>
                sql.commandIO(
                    sql"""
                INSERT INTO MiningAntiCheeser (
                    Health, ChunkX, ChunkZ, Y, World
                ) VALUES (
                    $int4, $int8, $int8, $int8, $uuid
                ) ON CONFLICT (ChunkX, ChunkZ, Y, World) DO UPDATE SET Health = EXCLUDED.Health;
                """,
                    (h, cx, cz, y, world),
                )
            }.start
        } yield ()

    def blockBrokenPartA(b: Block)(using
        Resource[IO, Session[IO]]
    ): IO[Option[Int]] =
        get(b)

    def blockBrokenPartB(b: Block, it: Option[Int]): Int =
        it.getOrElse(countEligibleBlocks(b))

    def blockBrokenPartC(b: Block, it: Int)(using
        Resource[IO, Session[IO]]
    ): IO[Boolean] =
        val health = it - 1
        for {
            _ <- set(b, health)
        } yield health >= 0
