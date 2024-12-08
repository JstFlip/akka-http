package highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.MediaType.Multipart
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Sink}

import java.io.File
import scala.util._


object UploadingFiles extends App {

  implicit val system: ActorSystem = ActorSystem("WebSockets")

  import system.dispatcher

  val filesRoute = {
    (pathEndOrSingleSlash & get) {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          |   <form action="http://localhost:80/upload" method="post" enctype="multipart/form-data">
          |     <input type="file" name="customFile">
          |     <button type="submit">Upload</button>
          |   </form>
          | </body>
          |</html>
          |""".stripMargin
      ))
    } ~
    (path("upload") & extractLog) { log =>
      entity(as[Multipart.FormData]) { formData =>
        val partsSource = formData.parts
        val filePartsSink = Sink.foreach[Multipart.FormData.BodyPart] { part =>
          if (part.name == "customFile") {
            val filePath = s"src/main/resources/download/${part.filename.getOrElse("tempFile_" + System.currentTimeMillis())}"
            val file = new File(filePath)
            log.info(s"Writing to file: $filePath")

            val fileContentsSource = part.entity.dataBytes
            val fileContentsSink = FileIO.toPath(file.toPath)

            fileContentsSource.runWith(fileContentsSink)
          }
        }

        val writeOperationFut = partsSource.runWith(filePartsSink)

        onComplete(writeOperationFut) {
          case Success(_) => complete("File uploaded")
          case Failure(exception) => complete(s"File upload failed: $exception")
        }
      }
    }
  }

  Http().newServerAt("localhost", 80).bind(filesRoute)

}
