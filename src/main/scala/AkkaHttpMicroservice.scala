import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, HttpHeader}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.{IOException, InputStream}
import spray.json._
import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DefaultJsonProtocol

case class ReceivedHeader(name: String, value: String)

case class ReceivedParameter(name: String, value: String)

case class ResponseJson(method: String, headers: Seq[ReceivedHeader], params: Seq[ReceivedParameter])

trait Protocols extends DefaultJsonProtocol {
  implicit val headerFormat = jsonFormat2(ReceivedHeader.apply)
  implicit val paramFormat = jsonFormat2(ReceivedParameter.apply)
  implicit val responseJson = jsonFormat3(ResponseJson.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  def marshalResponse(method: String, parameters: Seq[(String, String)], headers: Seq[HttpHeader]): ToResponseMarshallable = {
    val headrs = headers.map( h => ReceivedHeader(h.name(), h.value()))
    val parms = parameters.map( p => ReceivedParameter(p._1, p._2))
    ToResponseMarshallable(ResponseJson(method, headrs, parms).toJson)
  }

  val routes =
    pathPrefix("") {
      logRequest("Received") {
        parameterMap { pm =>
          extractRequest { req =>
            get {
              complete(marshalResponse("get", pm.toSeq, req.headers))
            } ~
            post {
              complete(marshalResponse("post", pm.toSeq, req.headers))
            } ~
            put {
              complete(marshalResponse("put", pm.toSeq, req.headers))
            } ~
            delete {
              complete(marshalResponse("delete", pm.toSeq, req.headers))
            } ~
            head {
              complete(marshalResponse("head", pm.toSeq, req.headers))
            } ~
            options {
              complete(marshalResponse("options", pm.toSeq, req.headers))
            }
          }
        }
      }
    }
}

object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
