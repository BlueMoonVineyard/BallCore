package BallCore.WebHooks

import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager
import BallCore.Storage.Migration
import skunk.implicits._
import skunk.Session
import skunk.Transaction
import cats.effect.IO
import BallCore.Groups.UserID
import BallCore.Groups.GroupID
import BallCore.Groups.nullUUID
import BallCore.Groups.Permissions
import BallCore.Groups.GroupError
import skunk.codec.all._
import cats.data.EitherT
import org.http4s.client.Client
import org.http4s.Request
import org.http4s.Method
import org.http4s.Uri
import org.http4s.ParseFailure
import io.circe.Json
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.EntityDecoder
import org.http4s.circe._
import cats.syntax.traverse._
import org.http4s.client.UnexpectedStatus
import org.http4s.Status
import net.kyori.adventure.text.Component
import BallCore.TextComponents._

enum WebHookError:
    case invalidURI(error: ParseFailure)
    case validationFailed(error: Throwable)
    case groupError(error: GroupError)

    def explain: Component =
        this match
            case invalidURI(error) =>
                txt"the provided URI was invalid: ${error}"
            case validationFailed(error) =>
                txt"a test message failed to be sent: ${error}"
            case groupError(error) =>
                Component.text(error.explain())

case class DiscordMessage(id: String)

object DiscordMessage:
    given Encoder[DiscordMessage] = deriveEncoder[DiscordMessage]
    given Decoder[DiscordMessage] = deriveDecoder[DiscordMessage]
    given EntityDecoder[IO, DiscordMessage] = jsonOf[IO, DiscordMessage]

class WebHookManager(using gm: GroupManager, sql: SQLManager, http: Client[IO]):
    sql.applyMigration(
        Migration(
            "Initial WebHook manager",
            List(
                sql"""
                CREATE TABLE Webhooks (
                    Webhook TEXT NOT NULL,
                    GroupID UUID NOT NULL REFERENCES GroupStates(ID) ON DELETE CASCADE,
                    UNIQUE(Webhook, GroupID)
                );
                """.command
            ),
            List(
                sql"""
                DROP TABLE Webhooks;
                """.command
            ),
        )
    )

    val urlCodec =
        text.eimap[Uri](x => Uri.fromString(x).left.map(_.toString))(uri =>
            uri.toString
        )

    private def validateWebHook(url: Uri): EitherT[IO, WebHookError, Unit] =
        val request =
            Request[IO](
                method = Method.POST,
                uri = url.withQueryParam("wait", "true"),
            ).withEntity(
                Json.fromFields(
                    List(
                        "content" -> Json.fromString(
                            "Test message from CivCubed."
                        )
                    )
                )
            )
        EitherT(
            http
                .expect[DiscordMessage](request)
                .map(_ => Right(()))
                .recoverWith(x =>
                    IO.pure(Left(WebHookError.validationFailed(x)))
                )
        )

    def addWebHook(as: UserID, group: GroupID, url: String)(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[WebHookError, Unit]] =
        (for {
            uri <- EitherT.fromEither(
                Uri.fromString(url).left.map(WebHookError.invalidURI.apply)
            )
            _ <- gm
                .checkE(
                    as,
                    group,
                    nullUUID,
                    Permissions.UpdateGroupInformation,
                )
                .leftMap(WebHookError.groupError.apply)
            _ <- validateWebHook(uri)
            _ <- EitherT.right(
                sql.commandIO(
                    sql"""
                    INSERT INTO Webhooks (
                        Webhook, GroupID
                    ) VALUES (
                        $urlCodec, $uuid
                    );
                    """,
                    (uri, group),
                )
            )
        } yield ()).value

    def removeWebHook(as: UserID, group: GroupID, url: String)(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[WebHookError, Unit]] =
        (for {
            uri <- EitherT.fromEither(
                Uri.fromString(url).left.map(WebHookError.invalidURI.apply)
            )
            _ <- gm
                .checkE(
                    as,
                    group,
                    nullUUID,
                    Permissions.UpdateGroupInformation,
                )
                .leftMap(WebHookError.groupError.apply)
            _ <- EitherT.right(
                sql.commandIO(
                    sql"""
                    DELETE FROM Webhooks WHERE GroupID = $uuid AND Webhook = $urlCodec;
                    """,
                    (group, uri),
                )
            )
        } yield ()).value

    def getWebHooksFor(as: UserID, group: GroupID)(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[WebHookError, List[Uri]]] =
        (for {
            _ <- gm
                .checkE(
                    as,
                    group,
                    nullUUID,
                    Permissions.UpdateGroupInformation,
                )
                .leftMap(WebHookError.groupError.apply)
            hooks <- EitherT.right(
                sql.queryListIO(
                    sql"""
                SELECT Webhook FROM Webhooks WHERE GroupID = $uuid;
                """,
                    urlCodec,
                    group,
                )
            )
        } yield hooks).value

    def broadcastTo(group: GroupID, message: String)(using
        Session[IO],
        Transaction[IO],
    ): IO[Either[WebHookError, Unit]] =
        (for {
            hooks <- EitherT.right(
                sql.queryListIO(
                    sql"""
                SELECT Webhook FROM Webhooks WHERE GroupID = $uuid;
                """,
                    urlCodec,
                    group,
                )
            )
            _ <- EitherT.right(hooks.traverse { hook =>
                val request =
                    Request[IO](
                        method = Method.POST,
                        uri = hook.withQueryParam("wait", "true"),
                    ).withEntity(
                        Json.fromFields(
                            List(
                                "content" -> Json.fromString(
                                    message
                                )
                            )
                        )
                    )
                http
                    .expect[DiscordMessage](request)
                    .recoverWith {
                        case UnexpectedStatus(Status.NotFound, _, _) =>
                            sql.commandIO(
                                sql"""
                            DELETE FROM Webhooks WHERE Webhook = $urlCodec
                            """,
                                hook,
                            )
                    }
            })
        } yield ()).value
