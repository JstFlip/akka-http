package highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.duration.DurationInt

object HighLevelServer extends App with GuitarStoreJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("HighLevelServer")

  import system.dispatcher
  import GuitarDB._

  /*
    GET /api/guitar
    GET /api/guitar?id=<id> OR /api/guitar/<id>
    GET /api/guitar/inventory?inStock=<true/false>
   */

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = Seq(
    Guitar("Test", "test"),
    Guitar("Test1", "test1", 0),
    Guitar("Test2", "test2")
  )
  guitarList.foreach { g =>
    guitarDb ! CreateGuitar(g)
  }

  implicit val timeout: Timeout = Timeout(600.seconds)

  val guitarServerRoute =
    path("api" / "guitar") {
      parameter("id".as[Int]) { guitarId =>
        get {
          val guitarsFut = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFut = guitarsFut.map { guitarOpt =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOpt.toJson.prettyPrint
            )
          }
          complete(entityFut)
        }
      } ~
        get {
          val guitarsFut = (guitarDb ? FindAllGuitars).mapTo[Seq[Guitar]]
          val entityFut = guitarsFut.map { guitars =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          }
          complete(entityFut)
        }
    } ~
      path("api" / "guitar" / IntNumber) { guitarId =>
        get {
          val guitarsFut = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFut = guitarsFut.map { guitarOpt =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOpt.toJson.prettyPrint
            )
          }
          complete(entityFut)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
          parameter("inStock".as[Boolean]) { inStock =>
            val guitarsFut = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[Seq[Guitar]]
            val entityFut = guitarsFut.map { guitars =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            }
            complete(entityFut)
          }
        }
      }

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter("inStock".as[Boolean]) { inStock =>
          complete(
            (guitarDb ? FindGuitarsInStock(inStock))
              .mapTo[Seq[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
        (path(IntNumber) | parameter("id".as[Int])) { guitarId =>
          complete(
            (guitarDb ? FindGuitar(guitarId))
              .mapTo[Option[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        } ~
        pathEndOrSingleSlash {
          onComplete(
            (guitarDb ? FindAllGuitars)
              .mapTo[Seq[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          ) { x => complete(x)}
        }
    }

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(simplifiedGuitarServerRoute)
}
