package CivCubed.PVPServer

import CivCubed.Storage.SQLManager
import CivCubed.Storage.Migration
import skunk.codec.all.*
import skunk.implicits.*
import java.util.UUID
import skunk.Session
import cats.effect.IO
import skunk.SqlState
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.LiteralArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.inventory.ItemStack
import java.io.ObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ByteArrayOutputStream
import CivCubed.TextComponents.*
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.SuggestionInfo
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import com.mojang.brigadier.suggestion.Suggestions
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import scala.jdk.FutureConverters.*

def itemStacksToByteArray(itemStacks: Array[ItemStack]): Array[Byte] =
    val blobbed = itemStacks.map(Option(_)).map(_.map(_.serializeAsBytes))

    val it = ByteArrayOutputStream()
    val oo = ObjectOutputStream(it)
    oo.writeObject(blobbed)
    oo.flush()
    it.flush()
    it.toByteArray()

def itemStacksFromByteArray(itemStacks: Array[Byte]): Array[ItemStack] =
    val it = ByteArrayInputStream(itemStacks)
    val oi = ObjectInputStream(it)
    val unblobbed = oi.readObject().asInstanceOf[Array[Option[Array[Byte]]]]
    unblobbed.map(_.map(ItemStack.deserializeBytes)).map(_.orNull)

object Loadouts:
    def register()(using sql: SQLManager): Unit =
        given LoadoutManager = LoadoutManager()
        LoadoutsCommand().node.register()

class LoadoutsCommand(using sql: SQLManager, lm: LoadoutManager):
    def suggestPublic(
        context: SuggestionInfo[CommandSender],
        builder: SuggestionsBuilder,
    ): CompletableFuture[Suggestions] =
        sql.useFuture {
            sql.withS(lm.getPublicLoadouts())
                .map { loadouts =>
                    loadouts.foreach(builder.suggest)
                    builder.build()
                }
        }.asJava
            .toCompletableFuture()
    def suggestPrivate(
        context: SuggestionInfo[CommandSender],
        builder: SuggestionsBuilder,
    ): CompletableFuture[Suggestions] =
        val player = context.sender().asInstanceOf[Player]

        sql.useFuture {
            sql.withS(lm.getLoadouts(player.getUniqueId))
                .map { loadouts =>
                    loadouts.foreach(builder.suggest)
                    builder.build()
                }
        }.asJava
            .toCompletableFuture()
    val node = CommandTree("loadouts")
        .withRequirement(_.hasPermission("civcubed.loadouts"))
        .`then`(
            LiteralArgument("save")
                .`then`(
                    StringArgument("name")
                        .executesPlayer({ (player, args) =>
                            val loadoutName = args.getUnchecked[String]("name")
                            sql.useFireAndForget(
                                sql.withS(for
                                    _ <- lm.setLoadout(
                                        player.getUniqueId,
                                        loadoutName,
                                        itemStacksToByteArray(
                                            player.getInventory.getContents
                                        ),
                                    )
                                    _ <- IO {
                                        player.sendServerMessage(
                                            trans"commands.loadouts.saved".arguments(loadoutName.toComponent)
                                        )
                                    }
                                yield ())
                            )
                        }: PlayerCommandExecutor)
                )
        )
        .`then`(
            LiteralArgument("publish")
                .`then`(
                    StringArgument("name")
                        .replaceSuggestions(suggestPrivate)
                        .`then`(
                            LiteralArgument("as").`then`(
                                StringArgument("published name")
                                    .executesPlayer({ (player, args) =>
                                        val loadoutName =
                                            args.getUnchecked[String]("name")
                                        val publishedName = args
                                            .getUnchecked[String](
                                                "published name"
                                            )

                                        sql.useFireAndForget(
                                            sql.withS(
                                                for
                                                    result <- lm.publishLoadout(
                                                        player.getUniqueId,
                                                        loadoutName,
                                                        publishedName,
                                                    )
                                                    _ <- IO {
                                                        result match
                                                            case Left(
                                                                    LoadoutError.alreadyExists
                                                                ) =>
                                                                player.sendServerMessage(
                                                                    trans"commands.loadouts.published-already-exists".arguments(publishedName.toComponent)
                                                                )
                                                            case Right(value) =>
                                                                player.sendServerMessage(
                                                                    trans"commands.loadouts.published-successfully".arguments(loadoutName.toComponent, publishedName.toComponent)
                                                                )
                                                    }
                                                yield ()
                                            )
                                        )
                                    }: PlayerCommandExecutor)
                            )
                        )
                )
        )
        .`then`(
            LiteralArgument("apply")
                .`then`(
                    LiteralArgument("private")
                        .`then`(
                            StringArgument("name")
                                .replaceSuggestions(suggestPrivate)
                                .executesPlayer({ (player, args) =>
                                    val name = args.getUnchecked[String]("name")
                                    sql.useBlocking(
                                        sql.withS(
                                            lm.getLoadout(
                                                player.getUniqueId,
                                                name,
                                            )
                                        )
                                    ).map(itemStacksFromByteArray) match
                                        case None =>
                                            player.sendServerMessage(
                                                trans"commands.loadouts.private.not-exist"
                                            )
                                        case Some(ok) =>
                                            player
                                                .getInventory()
                                                .setContents(ok)
                                            player.sendServerMessage(
                                                trans"commands.loadouts.private.applied".arguments(name.toComponent)
                                            )
                                }: PlayerCommandExecutor)
                        )
                )
                .`then`(
                    LiteralArgument("public")
                        .`then`(
                            StringArgument("name")
                                .replaceSuggestions(suggestPublic)
                                .executesPlayer({ (player, args) =>
                                    val name = args.getUnchecked[String]("name")
                                    sql.useBlocking(
                                        sql.withS(
                                            lm.getPublicLoadout(
                                                name
                                            )
                                        )
                                    ).map(itemStacksFromByteArray) match
                                        case None =>
                                            player.sendServerMessage(
                                                trans"commands.loadouts.public.not-exist"
                                            )
                                        case Some(ok) =>
                                            player
                                                .getInventory()
                                                .setContents(ok)
                                            player.sendServerMessage(
                                                trans"commands.loadouts.public.applied".arguments(name.toComponent)
                                            )
                                }: PlayerCommandExecutor)
                        )
                )
        )

enum LoadoutError:
    case alreadyExists

class LoadoutManager(using sql: SQLManager):
    sql.applyMigration(
        Migration(
            "Initial Loadout Manager",
            List(
                sql"""
                CREATE TABLE Loadouts (
                    Name TEXT NOT NULL,
                    Creator UUID NOT NULL,
                    PublicName TEXT UNIQUE,
                    Inventory BYTEA NOT NULL,
                    UNIQUE(Name, Creator)
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE Loadouts;
                """.command
            ),
        )
    )

    def setLoadout(as: UUID, name: String, inventory: Array[Byte])(using
        Session[IO]
    ): IO[Unit] =
        sql.commandIO(
            sql"""
        INSERT INTO Loadouts (
            Name, Creator, Inventory
        ) VALUES (
            $text, $uuid, $bytea
        ) ON CONFLICT (Name, Creator) DO UPDATE SET Inventory = EXCLUDED.Inventory;
        """,
            (name, as, inventory),
        ).map(_ => ())

    def publishLoadout(as: UUID, name: String, publicName: String)(using
        Session[IO]
    ): IO[Either[LoadoutError, Unit]] =
        sql.commandIO(
            sql"""
        UPDATE Loadouts SET PublicName = $text WHERE Name = $text AND Creator = $uuid;
        """,
            (publicName, name, as),
        ).redeemWith(
            { case SqlState.UniqueViolation(_) =>
                IO.pure(Left(LoadoutError.alreadyExists))
            },
            { _ =>
                IO.pure(Right(()))
            },
        )

    def getLoadout(as: UUID, name: String)(using
        Session[IO]
    ): IO[Option[Array[Byte]]] =
        sql.queryOptionIO(
            sql"""
        SELECT Inventory FROM Loadouts WHERE Name = $text AND Creator = $uuid;
        """,
            bytea,
            (name, as),
        )

    def getLoadouts(as: UUID)(using Session[IO]): IO[List[String]] =
        sql.queryListIO(
            sql"""
        SELECT Name FROM Loadouts WHERE Creator = $uuid;
        """,
            text,
            as,
        )

    def getPublicLoadout(
        name: String
    )(using Session[IO]): IO[Option[Array[Byte]]] =
        sql.queryOptionIO(
            sql"""
        SELECT Inventory FROM Loadouts WHERE PublicName = $text;
        """,
            bytea,
            (name),
        )

    def getPublicLoadouts()(using Session[IO]): IO[List[String]] =
        sql.queryListIO(
            sql"""
        SELECT PublicName FROM Loadouts WHERE PublicName IS NOT NULL;
        """,
            text,
            skunk.Void,
        )
