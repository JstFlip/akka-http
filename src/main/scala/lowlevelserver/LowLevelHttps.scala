package lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.model._

import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.Future

object HttpsContext {
  // key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystoreFile = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  val password = "akka-https".toCharArray
  ks.load(keystoreFile, password)

  // key manager
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, password)

  // initialize trust manager
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // initialize an SSL ctx
  val sslContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  // return the https connection context
  val httpsConnectionContext = ConnectionContext.httpsServer(sslContext)
}

object LowLevelHttps extends App {

  implicit val system: ActorSystem = ActorSystem("LowLevelHttps")
  import system.dispatcher

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
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

  Http()
    .newServerAt("localhost", 8443)
    .enableHttps(HttpsContext.httpsConnectionContext)
    .bind(requestHandler)
}
