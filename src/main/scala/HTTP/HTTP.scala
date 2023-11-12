package BallCore.HTTP

import BallCore.DataStructures.ShutdownCallbacks
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*

object HTTP:
  val router =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>uwu</h1>"))
      }
    }

  def register()(using cb: ShutdownCallbacks) =
    given system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "ballcore")

    given aec: concurrent.ExecutionContext = system.executionContext

    val bindingFuture
    : concurrent.Future[akka.http.scaladsl.Http.ServerBinding] =
      Http().newServerAt("localhost", 7543).bind(router)
    cb.add { () =>
      bindingFuture
        .flatMap(_.unbind())
        .map(_ => system.terminate())
    }
