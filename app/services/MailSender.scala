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
import play.api.libs.mailer._

@Singleton
class MailSender @Inject()(playConfig:Configuration, esClientMgr:ESClientManager)
                          (implicit mat:Materializer, injector: Injector, mailerClient: MailerClient){
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
        logger.debug(s"Warning hosts: - ")
        for (hostNameString <- warningHosts) {
          logger.debug(hostNameString)
        }
        logger.debug(s"Problem hosts: - ")
        for (hostNameString <- problemHosts) {
          logger.debug(hostNameString)
        }

        if ((warningHosts.length > 0) || (problemHosts.length > 0)) {
          var mailBody = s""

          if (problemHosts.length > 0) {
            mailBody = mailBody + s"\\n The following machines have the status 'problem': -"
            for (hostNameString <- problemHosts) {
              mailBody = mailBody + s"\\n $hostNameString"
            }
            mailBody = mailBody + s"\\n"
          }

          if (warningHosts.length > 0) {
            mailBody = mailBody + s"\\n The following machines have the status 'warning': -"
            for (hostNameString <- warningHosts) {
              mailBody = mailBody + s"\\n $hostNameString"
            }
            mailBody = mailBody + s"\\n"
          }

          logger.info( s"About to attempt to send an e-mail to: ${playConfig.get[String]("mail.recipient_address")}")
          try {
            val email = Email( playConfig.get[String]("mail.summary_subject"), s"${playConfig.get[String]("mail.sender_name")} <${playConfig.get[String]("mail.sender_address")}>", Seq(s"${playConfig.get[String]("mail.recipient_name")} <${playConfig.get[String]("mail.recipient_address")}>"), bodyText = Some(mailBody))
            mailerClient.send(email)
          } catch {
            case e: Exception => logger.error(s"Sending e-mail failed with error: $e")
          }
        }
    })
  }
}

