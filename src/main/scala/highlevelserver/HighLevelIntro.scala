package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import lowlevelserver.HttpsContext

object HighLevelIntro extends App {

  implicit val system: ActorSystem = ActorSystem("HighLevelIntro")
  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute =
    path("home") {
      complete(StatusCodes.OK)
    }

  val pathGetRoute =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

  // chaining directives
  val chainedRoute =
    path("endpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        post {
          complete(StatusCodes.Forbidden)
        }
    } ~
      path("home") {
        complete(HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   asdasdads
            | </body>
            |</html>
            |""".stripMargin
        ))
      }

  Http()
    .newServerAt("localhost", 80)
    .enableHttps(HttpsContext.httpsConnectionContext)
    .bind(chainedRoute)
}
