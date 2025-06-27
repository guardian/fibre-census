package services

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import helpers.ESClientManager
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.inject.Injector

import java.sql.Timestamp
import java.time.{Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import play.api.libs.json.{JsObject, JsValue, Json}

@Singleton
class MailSender @Inject()(playConfig:Configuration, esClientMgr:ESClientManager)
                          (implicit mat:Materializer, injector: Injector){
  private val logger = LoggerFactory.getLogger(getClass)
  import com.sksamuel.elastic4s.http.ElasticDsl._

  protected val indexName = playConfig.get[String]("elasticsearch.indexName")

  def sendMail():Future[Unit] = {
    val client = esClientMgr.getClient()

    logger.info(s"Mail sender run")

    client.execute {
      search(s"$indexName").query("*").size(1000)
    }.map({
      case Left(failure) =>
        logger.debug( s"Could not load records.")
      case Right(output) =>
        var warningHosts: Array[String] = new Array[String](0)
        var problemHosts: Array[String] = new Array[String](0)
        val response = output.body
        val responseObject = Json.parse(response.get)
        logger.debug( s"$responseObject")
        val hitsResult = (responseObject \ "hits" \ "hits")
        val hitsObject = hitsResult.get
        logger.debug( s"$hitsObject")
        val hitList = hitsObject.as[List[JsValue]]

        var status = ""
        var hostName = ""

        for (record <- hitList) {
          logger.debug(record.toString())
          try {
            val hostNameObject = (record \ "_source" \ "hostName")
            hostName = hostNameObject.get.toString().replace("\"", "")
            logger.debug(s"Host name: $hostName")
          } catch {
            case e:Exception => logger.debug(s"Could not get host name.")
          }
          try {
            val statusObject = (record \ "_source" \ "status")
            status = statusObject.get.toString().replace("\"", "")
            logger.debug(s"Status: $status")
          } catch {
            case e:Exception => logger.debug(s"Could not get status.")
          }

          if (status == "warning") {
            warningHosts = warningHosts :+ hostName
          }
          if (status == "problem") {
            problemHosts = problemHosts :+ hostName
          }
        }

        logger.debug(s"Warning hosts: ${warningHosts.mkString("Array(", ", ", ")")}")
        logger.debug(s"Problem hosts: ${problemHosts.mkString("Array(", ", ", ")")}")
    })
  }
}

