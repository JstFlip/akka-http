package lowlevelserver

import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.Http._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.scaladsl._
import lowlevelserver.GuitarDB._
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

case class Guitar(make: String, model: String, quantity: Int = 0)

class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map.empty
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toSeq
    case FindGuitar(id) =>
      log.info(s"Searching for guitar by id: $id")
      sender() ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar '$guitar' with id '$currentGuitarId'")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case AddQuantity(id, quantity) =>
      log.info(s"Trying to add $quantity items for guitar id $id")
      val guitar = guitars.get(id)
      val newGuitar = guitar.map {
        case Guitar(make, model, q) => Guitar(make, model, q + quantity)
      }
      newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
      sender() ! newGuitar
    case FindGuitarsInStock(inStock) =>
      log.info(s"Searching for guitars where inStock = $inStock")
      if (inStock)
        sender() ! guitars.values.filter(_.quantity > 0)
      else
        sender() ! guitars.values.filter(_.quantity == 0)
  }

}
object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
  case class AddQuantity(id: Int, quantity: Int)
  case class FindGuitarsInStock(inStock: Boolean)
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat3(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system: ActorSystem = ActorSystem("LowLevelServerAPI")
  import system.dispatcher

  /*
    GET /api/guitar => fetch all guitars from DB
    POST /api/guitar => insert into DB
   */

  // marshaling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson)

  // un-marshaling
  val simpleGuitarJsonStirng = "{\"make\":\"Fender\",\"model\":\"Stratocaster\", \"quantity\": 3}"
  println(simpleGuitarJsonStirng.parseJson.convertTo[Guitar])

  /*
    Setup
   */
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = Seq(
    Guitar("Test", "test"),
    Guitar("Test1", "test1", 69),
    Guitar("Test2", "test2")
  )

  guitarList.foreach { g =>
    guitarDb ! CreateGuitar(g)
  }
  /*
    Server
   */
  implicit val defaultTimeout = Timeout(2.seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt)

    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val guitarId = query.get("id").map(_.toInt)
      val guitarQuantity = query.get("quantity").map(_.toInt)

      val validGuitarResponse = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFut = (guitarDb ? AddQuantity(id, quantity)).mapTo[Option[Guitar]]
        newGuitarFut.map(_ => HttpResponse(StatusCodes.OK))
      }
      validGuitarResponse.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      val query = uri.query()
      val inStockOpt = query.get("inStock").map(_.toBoolean)
      inStockOpt match {
        case Some(inStock) =>
          val guitarsFut = (guitarDb ? FindGuitarsInStock(inStock)).mapTo[Seq[Guitar]]
          guitarsFut.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      val query = uri.query()
      if (query.isEmpty) {
        val guitarsFuture: Future[Seq[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[Seq[Guitar]]
        guitarsFuture.map { guitars =>
          HttpResponse(
            200,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
      } else {
        getGuitar(query)
      }
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      val strictEntityFuture = entity.toStrict(3.seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().newServerAt("localhost", 80).bind(requestHandler)

}
