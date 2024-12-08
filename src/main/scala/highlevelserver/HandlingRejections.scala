package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler}

object HandlingRejections extends App {
  implicit val system: ActorSystem = ActorSystem("HandlingRejections")

  import system.dispatcher

  val route =
    path("api" / "endpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        parameter("id") { _ =>
          complete(StatusCodes.OK)
        }
    }

  // Rejection handler
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"Got rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }
  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"Got rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val route2 =
    handleRejections(badRequestHandler) {
      path("api" / "endpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) {
              parameter("param") { _ =>
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }

  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"Got query param rejection: $m")
        complete("Rejected query param")
    }
    .handle {
      case m: MethodRejection =>
        println(s"Got method rejection: $m")
        complete("Rejected method")
    }
    .result()

  Http().newServerAt("localhost", 80).bind(route2)
}
