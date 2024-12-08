package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._

object DirectivesBreakdown extends App {

  implicit val system: ActorSystem = ActorSystem("HighLevelIntro")
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  // 1 - Filtering directives
  val simpleHttpMethodRoute =
    post { // eq dirs - get, put, patch, delete, head, options
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute =
    path("about") {
      complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "asdf"))
    }

  val complexPathRoute = // /api/endpoint
    path("api" / "endpoint") {
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash { // localhost:80 OR localhost:80/
      complete(StatusCodes.OK)
    }

  // 2 - Extraction directives
  // GET on /api/item/32
  val pathExtractionRoute =
    path("api" / "item" / IntNumber) { (num: Int) =>
      println(s"Got number in path: $num")
      complete(StatusCodes.OK)
    }

  val pathMultiExtractRoute =
    path("api" / "order" / IntNumber / IntNumber) { (id, id2) =>
      println(s"Got multiple numbers in path: $id, $id2")
      complete(StatusCodes.OK)
    }

  // 3 - Query directives
  val queryParamExtractionRoute =
  // GET on /api/item?id=41
    path("api" / "item") {
      parameter("id") { (itemId) =>
        println(s"Got parameter id: $itemId")
        complete(StatusCodes.OK)
      }
    } ~
      path("api" / "itemInt") {
        parameter("id".as[Int]) { (itemId) =>
          println(s"Got parameter id: $itemId")
          complete(StatusCodes.OK)
        }
      }

  val extractRequestRoute =
    path("api") {
      extractRequest { httpReq =>
        extractLog { log =>
          log.info(s"$httpReq")
          complete(StatusCodes.OK)
        }
      }
    }

  // 4 - composite directives
  val simpleNestedRoute =
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  // /about and /aboutUs
  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
      path ("aboutUs") {
        complete(StatusCodes.OK)
      }

  val groupedRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  // blog.com/432 AND blog.com?postId=432
  val blogByIdRoute =
    path(IntNumber) { blogPostId =>
      complete(StatusCodes.OK)
    }
  val blogByQueryParamRoute =
    parameter("postId".as[Int]) { blogPostId =>
      complete(StatusCodes.OK)
    }

  val groupedExtractionRoute =
    (path(IntNumber) | parameter("postId".as[Int])) { blogPostId =>
      complete(StatusCodes.OK)
    }

  // 5 - Actionable directives
  val completeOkRoute = complete(StatusCodes.OK)

  val failRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported"))
    }

  val routeWithRejection =
    path("home") {
      reject
    } ~
      path("index") {
        completeOkRoute
      }

  Http().newServerAt("localhost", 80).bind(routeWithRejection)

}
