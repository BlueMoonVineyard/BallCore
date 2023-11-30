package BallCore.OneTimeTeleport

import BallCore.Storage.SQLManager
import skunk.implicits._
import BallCore.Storage.Migration
import scala.collection.concurrent.TrieMap
import org.bukkit.entity.Player
import cats.effect.IO
import skunk.Session
import skunk.codec.all._
import cats.data.EitherT

enum OTTError:
    case alreadyUsedTeleport
    case isNotTeleportingToYou
    case teleportFailed

trait OneTimeTeleporterHooks:
    def teleport(source: Player, destination: Player): IO[Boolean]

class OneTimeTeleporter(hooks: OneTimeTeleporterHooks)(using
    sql: SQLManager
):
    sql.applyMigration(
        Migration(
            "Initial OTT",
            List(
                sql"""
                CREATE TABLE OneTimeTeleports (
                    MinecraftID UUID PRIMARY KEY
                );
                """.command
            ),
            List(
                sql"DROP TABLE OneTimeTeleports;".command
            ),
        )
    )

    private val pendingTeleports = TrieMap[Player, Player]()

    def requestTeleportTo(as: Player, target: Player)(using
        Session[IO]
    ): IO[Either[OTTError, Unit]] =
        (for {
            _ <- EitherT(
                sql.queryUniqueIO(
                    sql"""
            SELECT EXISTS(SELECT 1 FROM OneTimeTeleports WHERE MinecraftID = $uuid);
            """,
                    bool,
                    as.getUniqueId(),
                ).map {
                    case true => Left(OTTError.alreadyUsedTeleport)
                    case false => Right(())
                }
            )
            _ <- EitherT.right(IO { pendingTeleports(as) = target })
        } yield ()).value

    def acceptTeleportOf(as: Player, source: Player)(using
        Session[IO]
    ): IO[Either[OTTError, Unit]] =
        for {
            hasTeleport <- IO {
                pendingTeleports.get(source) == Some(as)
            }
            result <-
                if !hasTeleport then
                    IO.pure(Left(OTTError.isNotTeleportingToYou))
                else
                    for {
                        isOK <- hooks.teleport(source, as)
                        res <-
                            if !isOK then IO.pure(Left(OTTError.teleportFailed))
                            else
                                for {
                                    _ <- sql.commandIO(
                                        sql"""
                                    INSERT INTO OneTimeTeleports (
                                        MinecraftID
                                    ) VALUES (
                                        $uuid
                                    )
                                    """,
                                        source.getUniqueId(),
                                    )
                                    _ <- IO { pendingTeleports.remove(source) }
                                } yield Right(())
                    } yield res
        } yield result
