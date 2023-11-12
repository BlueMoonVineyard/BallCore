package BallCore.PluginMessaging

import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.jawn.decodeByteArray
import io.circe.syntax.*
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener

import java.nio.charset.StandardCharsets
import java.util.UUID

object ClientMessage:
  given Decoder[ClientMessage] = (c: HCursor) =>
    if c.keys.contains("id") then
      for {
        what <- c.downField("method").as[String]
        params <- c.downField("params").as[Json]
        id <- c.downField("id").as[Int]
      } yield ClientMessage.request(what, params, id)
    else
      for {
        what <- c.downField("method").as[String]
        params <- c.downField("params").as[Json]
      } yield ClientMessage.notification(what, params)

enum ClientMessage:
  case notification(what: String, params: Json)
  case request(what: String, params: Json, id: Int)

object ServerMessage:
  given Encoder[ServerMessage] = (to: ServerMessage) =>
    to.result match
      case Left(err) =>
        Json.obj(
          ("json-rpc", Json.fromString("2.0")),
          ("error", err.asJson),
          ("id", Json.fromInt(to.respondingTo))
        )
      case Right(res) =>
        Json.obj(
          ("json-rpc", Json.fromString("2.0")),
          ("result", res),
          ("id", Json.fromInt(to.respondingTo))
        )

object RPCError:
  given Encoder[RPCError] = deriveEncoder[RPCError]

case class RPCError(code: Int, message: String, data: Json)

case class ServerMessage(result: Either[RPCError, Json], respondingTo: Int)

object Messaging:
  val channel: String = NamespacedKey("civcubed", "integration").asString()

  def register()(using p: Plugin, gm: GroupManager, sql: SQLManager): Unit =
    p.getServer.getMessenger.registerOutgoingPluginChannel(p, channel)
    p.getServer.getMessenger
      .registerIncomingPluginChannel(p, channel, PluginMessaging())
    ()

class PluginMessaging()(using p: Plugin, gm: GroupManager, sql: SQLManager)
    extends PluginMessageListener:
  object Extensions:
    extension (plr: Player)
      def sendPluginMessage(ba: Array[Byte]): Unit =
        plr.sendPluginMessage(p, Messaging.channel, ba)

  import Extensions.*

  private val handlers
      : Map[String, (Player, Json) => IO[Either[RPCError, Json]]] = Map(
    "getGroups" -> lift(getGroups)
  )

  inline private def lift[Input, Output](
      fn: (Player, Input) => IO[Either[RPCError, Output]]
  )(using
      Decoder[Input],
      Encoder[Output]
  ): (Player, Json) => IO[Either[RPCError, Json]] = { (player, params) =>
    params.as[Input] match
      case Left(err) =>
        IO.pure(Left(RPCError(-32602, err.toString, Json.obj())))
      case Right(value) =>
        fn(player, value).map(_.map(_.asJson))
  }

  override def onPluginMessageReceived(
      channel: String,
      player: Player,
      message: Array[Byte]
  ): Unit =
    if channel != Messaging.channel then
      return

      decodeByteArray[ClientMessage](message) match
        case Left(err) =>
          player.sendServerMessage(
            txt"I received an invalid message from one of your mods: $err"
          )
        case Right(ClientMessage.notification(what, params)) =>
          ()
        case Right(ClientMessage.request(what, params, id)) =>
          handlers.get(what) match
            case None =>
              player.sendPluginMessage(
                ServerMessage(
                  Left(RPCError(-32601, "Method not found", Json.obj())),
                  id
                ).asJson.toString.getBytes(StandardCharsets.UTF_8)
              )
            case Some(handler) =>
              handler(player, params).unsafeRunAsync {
                case Left(exception) =>
                  player.sendPluginMessage(
                    ServerMessage(
                      Left(
                        RPCError(-32000, "Internal server error", Json.obj())
                      ),
                      id
                    ).asJson.toString.getBytes(StandardCharsets.UTF_8)
                  )
                case Right(Left(err)) =>
                  player.sendPluginMessage(
                    ServerMessage(Left(err), id).asJson.toString
                      .getBytes(StandardCharsets.UTF_8)
                  )
                case Right(Right(resp)) =>
                  player.sendPluginMessage(
                    ServerMessage(Right(resp), id).asJson.toString
                      .getBytes(StandardCharsets.UTF_8)
                  )
              }

  case class GetGroupsRequest()

  object GetGroupsRequest {
    given Decoder[GetGroupsRequest] = deriveDecoder[GetGroupsRequest]
  }

  case class GetGroupsResponse(groups: List[(String, UUID)])

  private object GetGroupsResponse {
    given Encoder[GetGroupsResponse] = deriveEncoder[GetGroupsResponse]
  }

  private def getGroups(
      player: Player,
      params: GetGroupsRequest
  ): IO[Either[RPCError, GetGroupsResponse]] =
    val _ = params
    sql.useIO {
      gm.userGroups(player.getUniqueId).value.map {
        case Left(err) =>
          Left(RPCError(-32000, err.explain(), err.asJson))
        case Right(ok) =>
          Right(GetGroupsResponse(ok.map { state => (state.name, state.id) }))
      }
    }
