package BallCoreVelocityPlugin

import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import com.velocitypowered.api.command.BrigadierCommand
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.velocitypowered.api.command.CommandSource
import com.mojang.brigadier.Command
import BallCore.LinkedAccounts.LinkedAccountsManager
import com.velocitypowered.api.proxy.Player
import BallCore.TextComponents._
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import BallCore.Storage.SQLManager
import net.kyori.adventure.text.format.TextDecoration
import java.nio.file.Path
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import scala.util.Try
import java.security.SecureRandom
import java.util.Base64
import akka.http.scaladsl.server.directives.Credentials
import BallCore.LinkedAccounts.LinkedAccountError
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets
import akka.actor.BootstrapSetup
import java.nio.file.Files
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.arguments.StringArgumentType
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import BallCore.Storage.Config

class HTTP(apiKey: String)(using lam: LinkedAccountsManager):
    def intoResponse(r: Either[LinkedAccountError, String]) =
        def plainText(s: String) = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s)

        r match
            case Left(LinkedAccountError.accountAlreadyLinked) =>
                HttpResponse(StatusCodes.NotFound, entity = plainText("accountAlreadyLinked"))
            case Left(LinkedAccountError.linkCodeDoesNotExist) => 
                HttpResponse(StatusCodes.NotFound, entity = plainText("linkCodeDoesNotExist"))
            case Left(LinkedAccountError.linkCodeWasNotStartedFromMinecraft) =>
                HttpResponse(StatusCodes.BadRequest, entity = plainText("linkCodeWasNotStartedFromMinecraft"))
            case Left(LinkedAccountError.linkCodeWasNotStartedFromDiscord) =>
                HttpResponse(StatusCodes.BadRequest, entity = plainText("linkCodeWasNotStartedFromDiscord"))
            case Right(value) =>
                HttpResponse(StatusCodes.OK, entity = plainText(value))

    val linkStartRoute =
        path("start" / Segment) { discordUserID =>
            post {
                complete {
                    intoResponse(lam.startLinkProcessFromDiscord(discordUserID))
                }
            }
        }
    val linkCompleteRoute =
        path("complete" / Segment) { discordUserID =>
            post {
                entity(as[String]) { linkCode =>
                    complete {
                        intoResponse(lam.finishLinkProcessFromDiscord(linkCode, discordUserID).map(_ => "ok"))
                    }
                }
            }
        }
    val router =
        authenticateOAuth2("civcubed api", check) { _ =>
            pathPrefix("link") {
                concat(linkStartRoute, linkCompleteRoute)
            }
        }
    def check(credentials: Credentials): Option[String] = credentials match {
        case p @ Credentials.Provided(token) if p.verify(apiKey) => Some(token)
        case _ => None
    }
    def register()(using l: Logger): (() => Future[Unit]) =
        val confText = new String(classOf[HTTP].getResourceAsStream("/ballcore-akka.conf").readAllBytes(), StandardCharsets.UTF_8)
        val thisAppsConfig = ConfigFactory.parseString(confText).resolve()
        val setup = BootstrapSetup(thisAppsConfig)
            .withClassloader(classOf[HTTP].getClassLoader())
        given system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "ballcore", setup)
        given aec: concurrent.ExecutionContext = system.executionContext

        val bindingFuture: concurrent.Future[akka.http.scaladsl.Http.ServerBinding] = Http().newServerAt("0.0.0.0", 7543).bind(router)
        Await.result(bindingFuture, Duration.Inf)
        { () =>
            bindingFuture
                .flatMap(_.unbind())
                .map(_ => system.terminate())
        }

object VerifyCommand:
    def explain(s: LinkedAccountError): String =
        s match
            case LinkedAccountError.accountAlreadyLinked => "The account is already linked."
            case LinkedAccountError.linkCodeDoesNotExist => "The link code does not exist."
            case LinkedAccountError.linkCodeWasNotStartedFromMinecraft => "The link code is meant to be used on Minecraft. Use it there!"
            case LinkedAccountError.linkCodeWasNotStartedFromDiscord => "The link code is meant to be used on Discord. Use it there!"

    def make()(using lam: LinkedAccountsManager): BrigadierCommand =
        val node = LiteralArgumentBuilder.literal[CommandSource]("link")
            .requires { _.isInstanceOf[Player] }
            .executes { context =>
                val player = context.getSource().asInstanceOf[Player]

                val linkCode = lam.startLinkProcessFromMinecraft(player.getUniqueId())
                linkCode match
                    case Left(err) =>
                        player.sendServerMessage(txt(explain(err)))
                    case Right(linkCode) =>
                        val link = txt("/link-code use").color(Colors.teal).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.copyToClipboard(s"/link-code use code:${linkCode}")).hoverEvent(HoverEvent.showText(txt"/link-code use code:${linkCode}"))
                        player.sendServerMessage(txt"Your link code is ${txt(linkCode.toUpperCase()).color(Colors.yellow).decorate(TextDecoration.BOLD)}. Use ${link} (click to copy the command) on Discord to link your Minecraft account to your Discord account.")

                Command.SINGLE_SUCCESS
            }
            .`then`(RequiredArgumentBuilder.argument[CommandSource, String]("token-from-discord", StringArgumentType.word())
                .executes { context =>
                    val player = context.getSource().asInstanceOf[Player]
                    val linkCode = context.getArgument("token-from-discord", classOf[String])

                    val result = lam.finishLinkProcessFromMinecraft(linkCode, player.getUniqueId())
                    result match
                        case Left(err) =>
                            player.sendServerMessage(txt(explain(err)))
                        case Right(_) =>
                            player.sendServerMessage(txt"Your accounts have been successfully linked!")

                    Command.SINGLE_SUCCESS
                }
            )
        BrigadierCommand(node)

final class VelocityPlugin(server: ProxyServer, logger: Logger, dataDirectory: Path):
    given Logger = logger

    var shutdownHTTP: () => Future[Unit] = null

    def onProxyInitialize(event: ProxyInitializeEvent): Unit =
        val loader = YamlConfigurationLoader.builder()
            .path(dataDirectory.resolve("config.yaml"))
            .build()
        Files.createDirectories(dataDirectory)

        val config = Try(loader.load()).get
        val apiKeyNode = config.node("secrets", "api-key")
        val apiKey = Option(apiKeyNode.getString()).getOrElse {
            val prng = SecureRandom()
            val bytes = new Array[Byte](64)
            prng.nextBytes(bytes)
            val str = Base64.getEncoder().encodeToString(bytes)
            apiKeyNode.set(str)
            loader.save(config)
            str
        }

        val databaseConfig = Config.from(config.node("database")) match
            case Left(err) =>
                throw Exception(s"failed to read config because ${err}")
            case Right(value) =>
                value

        given sql: SQLManager = SQLManager(databaseConfig)
        given lam: LinkedAccountsManager = LinkedAccountsManager()
        shutdownHTTP = HTTP(apiKey).register()

        val commandManager = server.getCommandManager()
        commandManager.register(VerifyCommand.make())

    def onProxyShutdown(event: ProxyShutdownEvent): Unit =
        Await.result(shutdownHTTP(), Duration.Inf)
