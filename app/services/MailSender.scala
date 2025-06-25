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
      search(s"$indexName").query("size=1000")
    }.map({
      case Left(failure) =>
        logger.debug( s"Could not load records.")
      case Right(output) =>
        val response = output.body
        val responseObject = Json.parse(response.get)
        //val oldStatusResult = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "status")
        //val oldStatus = oldStatusResult.get.toString().replace("\"", "")
        logger.debug( s"$responseObject")
    })

  }
}

