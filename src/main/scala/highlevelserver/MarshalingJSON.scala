package highlevelserver

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import highlevelserver.GameAreaMap._
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.duration.DurationInt

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayersByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}
class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._

  var players = Map.empty[String, Player]

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all players")
      sender() ! players.values.toSeq
    case GetPlayer(nickname) =>
      log.info(s"Getting player with nickname: $nickname")
      sender() ! players.get(nickname)
    case GetPlayersByClass(characterClass) =>
      log.info(s"Getting players with class: $characterClass")
      sender() ! players.values.toSeq.filter(_.characterClass == characterClass)
    case AddPlayer(player) =>
      log.info(s"Trying to add player: $player")
      players += player.nickname -> player
      sender() ! OperationSuccess
    case RemovePlayer(player) =>
      log.info(s"Trying to remove player: $player")
      players -= player.nickname
      sender() ! OperationSuccess
  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

object MarshalingJSON extends App with PlayerJsonProtocol with SprayJsonSupport {
  implicit val system: ActorSystem = ActorSystem("MarshalingJSON")
  import system.dispatcher

  val gameMap = system.actorOf(Props[GameAreaMap], "GameAreaMap")
  val players = Seq(
    Player("P1", "C1", 1),
    Player("P2", "C2", 10),
    Player("P3", "C3", 20)
  )
  players.foreach(gameMap ! AddPlayer(_))

  /*
    - GET /api/player
    - GET /api/player/<nickname>
    - GET /api/player?nickname=<nickname>
    - GET /api/player/class/<class>
    - POST /api/player
    - DELETE /api/player
   */

  implicit val timeout: Timeout = Timeout(50.seconds)

  val gameRoute =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { chClass =>
          val playersByClassFut = (gameMap ? GetPlayersByClass(chClass)).mapTo[Seq[Player]]
          complete(playersByClassFut)
        } ~
        (path(Segment) | parameter("nickname")) { nickname =>
          val playerOptFut = (gameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
          complete(playerOptFut)
        } ~
        pathEndOrSingleSlash {
          complete((gameMap ? GetAllPlayers).mapTo[Seq[Player]])
        }
      } ~
      post {
        entity(as[Player]) { player =>
          complete((gameMap ? AddPlayer(player)).map(_ => StatusCodes.OK))
        }
      } ~
      delete {
        entity(as[Player]) { player =>
          complete((gameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
        }
      }
    }

  Http().newServerAt("localhost", 8080).bind(gameRoute)

}
