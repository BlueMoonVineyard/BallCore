package BallCore.Commands

import BallCore.Storage.SQLManager
import org.bukkit.plugin.Plugin
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import cats.effect.IO
import BallCore.SpawnInventory.OresAndYou
import BallCore.SpawnInventory.FingerprintsAndYou
import BallCore.SpawnInventory.WorkstationsAndYou
import BallCore.SpawnInventory.BattlesAndYou
import BallCore.SpawnInventory.SigilsAndYou
import BallCore.SpawnInventory.SmelteryAndYou
import BallCore.Advancements.ViewOres
import BallCore.SpawnInventory.HeartsAndYou

class BookCommand(using
    storage: BallCore.Acclimation.Storage,
    sql: SQLManager,
    p: Plugin,
):
    val node =
        CommandTree("book")
            .`then`(
                LiteralArgument("ores-and-you")
                    .executesPlayer({ (sender, args) =>
                        ViewOres.grant(sender, "book_used")
                        sql.useFireAndForget(for {
                            book <- sql.withS(OresAndYou.viewForPlayer(sender))
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("battles-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- sql.withS(
                                BattlesAndYou.viewForPlayer(sender)
                            )
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("smeltery-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- sql.withS(
                                SmelteryAndYou.viewForPlayer(sender)
                            )
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("sigils-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- sql.withS(
                                SigilsAndYou.viewForPlayer(sender)
                            )
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("workstations-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- sql.withS(
                                WorkstationsAndYou.viewForPlayer(sender)
                            )
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("hearts-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- HeartsAndYou.viewForPlayer(sender)
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("fingerprints-and-you")
                    .executesPlayer({ (sender, args) =>
                        sql.useFireAndForget(for {
                            book <- FingerprintsAndYou.viewForPlayer(sender)
                            _ <- IO { sender.openBook(book) }
                        } yield ())
                    }: PlayerCommandExecutor)
            )
            .`then`(
                LiteralArgument("spawnbook")
                    .executesPlayer({ (sender, args) =>
                        sender.openBook(BallCore.SpawnInventory.Book.book)
                    }: PlayerCommandExecutor)
            )
