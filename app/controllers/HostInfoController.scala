package controllers

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.sksamuel.elastic4s.http.ElasticDsl.update
import helpers.{ESClientManager, ZonedDateTimeEncoder}
import play.api.libs.mailer._
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import play.api.mvc.{AbstractController, ControllerComponents, PlayBodyParsers}
import models.{HostInfo, MdcPing, RecentLogin}
import responses.{ErrorListResponse, GenericErrorResponse, ObjectListResponse}
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.http.{DefaultHttpErrorHandler, HttpErrorHandler, ParserConfiguration}
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.circe.Circe
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

@Singleton
class HostInfoController @Inject()(playConfig:Configuration,cc:ControllerComponents,
                                    defaultTempFileCreator:TemporaryFileCreator, esClientMgr:ESClientManager)(implicit system:ActorSystem, mailerClient: MailerClient)
  extends AbstractController(cc) with PlayBodyParsers with ZonedDateTimeEncoder with Circe {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._

  private val logger = Logger(getClass)
  implicit def materializer:Materializer = ActorMaterializer()


  protected val indexName = playConfig.get[String]("elasticsearch.indexName")
  protected val loginsIndex = playConfig.get[String]("elasticsearch.loginsIndexName")
  override def config:ParserConfiguration = {
    ParserConfiguration(2048*1024,10*1024*1024)
  }

  override def errorHandler:HttpErrorHandler = DefaultHttpErrorHandler

  override def temporaryFileCreator:TemporaryFileCreator = defaultTempFileCreator

  def validateMDCPing(mDCPing: Option[Seq[MdcPing]]): String = {
    if (!mDCPing.isEmpty) {
      val visibleMDCList = mDCPing.get.map(rec=>rec).filter(rec=>rec.visible)

      logger.debug(s"visibleMDCList is: ${visibleMDCList}")

      if(visibleMDCList.length == 0){
        return "problem"
      }

      if (visibleMDCList.length != mDCPing.get.length) {
        return "warning"
      }

      val highPacketCounts = mDCPing.get.map(rec=>rec).filter(rec=>rec.packetloss > 0)

      logger.debug(s"highPacketCounts is: ${highPacketCounts}")

      if (highPacketCounts.length > 0){
        return "warning"
      }
    } else {
      return "problem"
    }
    return "normal"
  }


  def validateRecord(entry: HostInfo): String = {
    if (entry.model!="Mac Pro" && entry.model!="Mac Studio"){
      return "unimportant"
    }

    if (entry.ipAddresses.length < 2){
      logger.debug(s"${entry.hostName} has no metadata network")
      return "unimportant"
    }

    if (!entry.fibreChannel.isEmpty) {
      val fcWWM = entry.fibreChannel.get.domains.map(dom=>dom.portWWN)

      if (fcWWM.length < 2) {
        logger.debug(s"${entry.hostName} has insufficient fibre interfaces")
        return "problem"
      }
    } else {
      return "problem"
    }

    if (entry.mdcPing.isEmpty) return "warning"
    val mDCStatus = validateMDCPing(entry.mdcPing)
    if (mDCStatus != "normal") return mDCStatus

    if (!entry.fibreChannel.isEmpty) {
      val fCLUNCount = entry.fibreChannel.get.domains.map(dom=>dom.lunCount)

      val actualLUNCount = fCLUNCount.filter(entry=>entry>0)

      logger.debug(s"actualLUNCount is: ${actualLUNCount}")

      if(!(actualLUNCount.head > 19)){
        logger.debug(s"${entry.hostName} only has $actualLUNCount LUNs visible (expected at least 20)")
        return "problem"
      }
    }

    if (entry.model == "Mac Studio") {
      if (!entry.denyDlcVolumes.isEmpty) {
        if (entry.denyDlcVolumes.get.head != "false") return "warning"
      } else {
        return "warning"
      }
    }

    if (!entry.sanMounts.isEmpty) {
      val sanMountsNames = entry.sanMounts.get.map(mount=>mount.name)
      if (sanMountsNames.length < 3) return "warning"
    } else {
      return "warning"
    }

    return "normal"
  }

  def isTriggerStatus(oldStatus: String, newStatus: String): Boolean = {
    if ((oldStatus == "normal") && ((newStatus == "problem") || (newStatus == "warning"))) {
      return true
    }
    if ((oldStatus == "warning") && (newStatus == "problem")) {
      return true
    }
    return false
  }

  def addRecord = Action.async(parse.xml) { request=>
    val client = esClientMgr.getClient()

    HostInfo.fromXml(request.body, ZonedDateTime.now()) match {
      case Right(entry)=>
        val idToUse = s"${entry.hostName}"
        val entryStatus = validateRecord(entry)
        logger.debug(s"Entry status is ${entryStatus}")
        client.execute {
            update(idToUse).in(s"$indexName/entry").docAsUpsert(entry)
        }.map({
          case Left(failure) => InternalServerError(GenericErrorResponse("elasticsearch_error", failure.error.toString).asJson)
          case Right(success) =>
            client.execute {
              search(s"$indexName/entry").query(idsQuery(idToUse))
            }.map({
              case Left(failure) =>
                logger.debug( s"Could not load record.")
              case Right(output) =>
                val response = output.body
                val responseObject = Json.parse(response.get)
                val oldStatusResult = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "status")
                val oldStatus = oldStatusResult.get.toString().replace("\"", "")
                logger.debug( s"Old status: $oldStatus")
                if (isTriggerStatus(oldStatus, entryStatus)) {
                  logger.debug( s"About to attempt to send an e-mail to: ${playConfig.get[String]("mail.recipient_address")}")
                  try {
                    val email = Email( playConfig.get[String]("mail.subject"), s"${playConfig.get[String]("mail.sender_name")} <${playConfig.get[String]("mail.sender_address")}>", Seq(s"${playConfig.get[String]("mail.recipient_name")} <${playConfig.get[String]("mail.recipient_address")}>"), bodyText = Some(s"The machine ${entry.hostName} has entered the status of '${entryStatus}'."))
                    mailerClient.send(email)
                  } catch {
                    case e: Exception => logger.error(s"Sending e-mail failed with error: $e")
                  }
                }
            })
            client.execute {
              update(idToUse).in(s"$indexName/entry").docAsUpsert (
                "status" -> entryStatus
              )
            }
            Ok(success.result.id)
        }).recoverWith({
          case ex:Throwable=>
            logger.error("Could not create host entry", ex)
            Future(InternalServerError(GenericErrorResponse("elasticsearch_error", ex.getLocalizedMessage).asJson))
        })
      case Left(err)=>
        Future(BadRequest(ErrorListResponse("bad_data", err).asJson))
    }
  }

  def simpleStringSearch(q:Option[String],start:Option[Int],length:Option[Int]) = Action.async {
    val cli = esClientMgr.getClient()

    val actualStart=start.getOrElse(0)
    val actualLength=length.getOrElse(50)

    q match {
      case Some(searchTerms) =>
        val searchString = s"*$searchTerms*"
        val responseFuture = cli.execute {
          search(indexName) query searchString from actualStart size actualLength
        }

        responseFuture.map({
          case Left(failure) =>
            InternalServerError(Json.obj("status" -> "error", "detail" -> failure.toString))
          case Right(results) =>
            val resultList = results.result.to[HostInfo] //using the HostInfoHitReader trait
            Ok(ObjectListResponse[IndexedSeq[HostInfo]]("ok","entry",resultList,results.result.totalHits.toInt).asJson)
        }).recover({
          case ex:Throwable=>
            logger.error("Could not process result from elastic: ", ex)
            InternalServerError(GenericErrorResponse("error",ex.toString).asJson)
        })
      case None => Future(BadRequest(GenericErrorResponse("error", "you must specify a query string with ?q={string}").asJson))
    }
  }
}
