package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)

  implicit val loginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
}

object JWT extends App with SprayJsonSupport {

  implicit val system: ActorSystem = ActorSystem("JWT")

  import system.dispatcher
  import SecurityDomain._

  val passwordDb = Map(
    "admin" -> "admin",
    "test" -> "test1"
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "secret"

  def checkPassowrd(username: String, password: String): Boolean =
    passwordDb.contains(username) && passwordDb(username) == password

  def createToken(username: String, expDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("flip.com")
    )

    JwtSprayJson.encode(claims, secretKey, algorithm)
  }

  def isTokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
    case Failure(_) => true
  }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassowrd(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) if isTokenExpired(token) =>
          complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired!"))
        case Some(token) if isTokenValid(token) =>
          complete("User accessed secure endpoint!")
        case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token invalid!"))
      }
    }

  val route = loginRoute ~ authenticatedRoute

  Http().newServerAt("localhost", 80).bind(route)
}
