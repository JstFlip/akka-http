package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler


object HandlingExceptions extends App {
  implicit val system: ActorSystem = ActorSystem("HandlingExceptions")

  import system.dispatcher

  val customExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val simpleRoute = {
    handleExceptions(customExceptionHandler) {
      path("api" / "people") {
        get {
          throw new RuntimeException("Ex")
        } ~
          post {
            parameter("id") { id =>
              if (id.length > 2)
                throw new NoSuchElementException(s"Parameter $id not found")
              complete(StatusCodes.OK)
            }
          }
      }
    }
  }

  Http().newServerAt("localhost", 80).bind(simpleRoute)
}
