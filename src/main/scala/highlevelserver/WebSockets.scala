package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.CompactByteString
import akka.http.scaladsl.server.Directives._

import scala.concurrent.duration.DurationInt

object WebSockets extends App {

  implicit val system: ActorSystem = ActorSystem("WebSockets")

  import system.dispatcher

  // Message: TextMessage vs BinaryMessage
  val textMessage = TextMessage(Source.single("Test text message"))
  val binaryMessage = BinaryMessage(Source.single(CompactByteString("Test binary message")))

  val html =
    """
      |<html>
      |    <head>
      |        <script>
      |            var exampleSocket = new WebSocket("ws://localhost:8080/greeter");
      |            console.log("Starting websocket...");
      |
      |            exampleSocket.onmessage = function(event) {
      |                var newChild = document.createElement("div");
      |                newChild.innerText = event.data;
      |                document.getElementById("1").appendChild(newChild)
      |            };
      |
      |            exampleSocket.onopen = function(event) {
      |                exampleSocket.send("Socket open");
      |            };
      |
      |            exampleSocket.send("Socket message");
      |        </script>
      |    </head>
      |
      |    <body>
      |        Websocket demo
      |        <div id="1"></div>
      |    </body>
      |</html>
      |""".stripMargin

  def websocketFlow: Flow[Message, Message, Any] = Flow[Message].map {
    case tm: TextMessage =>
      TextMessage(Source.single("Server res: ") ++ tm.textStream)
    case bm: BinaryMessage =>
      bm.dataStream.runWith(Sink.ignore)
      TextMessage(Source.single("Server received binary message..."))

  }

  val websocketRoute =
    (pathEndOrSingleSlash & get) {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        html
      ))
    } ~
      path("greeter") {
        handleWebSocketMessages(socialFlow)
      }

  Http().newServerAt("localhost", 8080).bind(websocketRoute)

  case class SocialPost(owner: String, content: String)

  val socialFeed = Source(Seq(
    SocialPost("OW1", "C1"),
    SocialPost("OW2", "C2"),
    SocialPost("OW3", "C3"),
    SocialPost("OW4", "C4")
  ))
  val socialMessages = socialFeed
    .throttle(1, 2.seconds)
    .map(sp => TextMessage(s"${sp.owner}: ${sp.content}"))

  val socialFlow = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),
    socialMessages
  )

}
