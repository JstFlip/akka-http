package highlevelserver

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat: RootJsonFormat[Book] = jsonFormat3(Book)
}

class RouteDSLSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {

  import RouteDSLSpec._

  "A digital lib backend" should {
    "return all the books" in {
      Get("/api/book") ~> libRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[Seq[Book]] shouldBe books
      }
    }
    "return a book with query param" in {
      Get("/api/book?id=0") ~> libRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(0, "A1", "T1"))
      }
    }
    "return a book with id in path" in {
      Get("/api/book/0") ~> libRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val strictEntFut = response.entity.toStrict(1.second)
        val strictEnt = Await.result(strictEntFut, 1.second)

        strictEnt.contentType shouldBe ContentTypes.`application/json`
        val book = strictEnt.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(0, "A1", "T1"))
      }
    }
    "not accept other methods than POST and GET" in {
      Delete("/api/book") ~> libRoute ~> check {
        rejections should not be empty

        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length shouldBe 2
      }
    }
    "return all the books of a given author" in {
      Get("/api/book/author/A1") ~> libRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[Seq[Book]] shouldBe books.filter(_.author == "A1")
      }
    }

  }

}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport {

  var books = Seq(
    Book(0, "A1", "T1"),
    Book(1, "A2", "T2"),
    Book(2, "A3", "T3"),
    Book(3, "A4", "T4")
  )

  val libRoute =
    pathPrefix("api" / "book") {
      (path("author" / Segment) & get) { author =>
        complete(books.filter(_.author == author))
      } ~
        get {
          (path(IntNumber) | parameter("id".as[Int])) { id =>
            complete(books.find(_.id == id))
          } ~
            pathEndOrSingleSlash {
              complete(books)
            }
        } ~
        post {
          entity(as[Book]) { book =>
            books = books :+ book
            complete(StatusCodes.OK)
          } ~
            complete(StatusCodes.BadRequest)
        }
    }

}
