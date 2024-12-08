package lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.Http._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util._

object LowLevelAPI extends App {

  implicit val system: ActorSystem = ActorSystem("LowLevelServerAPI")
  import system.dispatcher

  val serverSource = Http().newServerAt("localhost", 8000).connectionSource()
  val connectionSink = Sink.foreach[IncomingConnection] { conn =>
    println(s"Accepted incoming connection from: ${conn.remoteAddress}")
  }

//  val serverBindingFuture = serverSource.to(connectionSink).run()
//  serverBindingFuture.onComplete {
//    case Success(binding) =>
//      println("Server binding successful.")
//      // binding.unbind()
//      // binding.terminate(2.seconds)
//    case Failure(exception) => println(s"Server binding failed: $exception")
//  }

  /*
    1 - synchronously server HTTP responses
   */
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Test
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   404 - Not found
            | </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val httpSinkConnectionHandler = Sink.foreach[IncomingConnection] { conn =>
    conn.handleWithSyncHandler(requestHandler)
  }

  // Http().newServerAt("localhost", 8080).connectionSource().runWith(httpSinkConnectionHandler)
//  Http().newServerAt("localhost", 8080).bindSync(requestHandler)

  /*
    2 - asynchronously server HTTP responses
   */
  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Home
            | </body>
            |</html>
            |""".stripMargin
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   404 - Not found
            | </body>
            |</html>
            |""".stripMargin
        )
      ))
  }

  // Http().newServerAt("localhost", 8080).bind(asyncRequestHandler)

  /*
    3 - Async via akka streams
   */
  val steamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Home
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   404 - Not found
            | </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val bindingFuture = Http().newServerAt("localhost", 8080).bindFlow(steamsBasedRequestHandler)

  // unbind (shutdown) server and terminate actorsystem
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
