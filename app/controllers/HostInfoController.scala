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

      if (actualLUNCount.length > 0) {
        if(!(actualLUNCount.head > 19)){
          logger.debug(s"${entry.hostName} only has $actualLUNCount LUNs visible (expected at least 20)")
          return "problem"
        }
      } else {
        return "problem"
      }

    }

    if (entry.model == "Mac Studio") {
      if (!entry.denyDlcVolumes.isEmpty) {
        if (entry.denyDlcVolumes.get.length > 0) {
          if (entry.denyDlcVolumes.get.head != "false") return "warning"
        } else {
          return "warning"
        }
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
            var oldStatus = s""
            client.execute {
              search(s"$indexName/entry").query(idsQuery(idToUse))
            }.map({
              case Left(failure) =>
                logger.debug( s"Could not load record.")
              case Right(output) =>
                val response = output.body
                val responseObject = Json.parse(response.get)
                logger.debug(responseObject.toString())
                val oldStatusResult = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "status")
                oldStatus = oldStatusResult.get.toString().replace("\"", "")
                logger.debug( s"Old status: $oldStatus")
            })
            client.execute {
              update(idToUse).in(s"$indexName/entry").docAsUpsert (
                "status" -> entryStatus
              )
            }
            Thread.sleep(1000)
            client.execute {
              search(s"$indexName/entry").query(idsQuery(idToUse))
            }.map({
              case Left(failure) =>
                logger.debug( s"Could not load record.")
              case Right(output) =>
                val response = output.body
                val responseObject = Json.parse(response.get)
                logger.debug(responseObject.toString())
                var mailBody = s"<html><body>"
                if (entryStatus == "problem") {
                  mailBody = mailBody + s"<div style='color: #ff0000;'>The machine ${entry.hostName} has entered the status of '${entryStatus}'.</div>"
                } else {
                  mailBody = mailBody + s"<div style='color: #ff9000;'>The machine ${entry.hostName} has entered the status of '${entryStatus}'.</div>"
                }
                val lUNZero = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "fibreChannel" \ "domains" \ 0 \ "lunCount")
                val lUNOne = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "fibreChannel" \ "domains" \ 1 \ "lunCount")
                var lUNTotal = 0
                try {
                  lUNTotal = lUNZero.get.toString().toInt
                } catch {
                  case e:Exception =>
                    logger.debug(s"Could not get zeroth LUN reading.")
                }
                try {
                  lUNTotal = lUNTotal + lUNOne.get.toString().toInt
                } catch {
                  case e:Exception =>
                    logger.debug(s"Could not get first LUN reading.")
                }
                if (lUNTotal < 20) {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- LUN count:&nbsp;</div> <div style='float: left; color: #ff0000;'>$lUNTotal Expecting at least 20 LUNs visible on at least one interface</div>"
                }
                var wWNPorts: Array[String] = new Array[String](0)
                try {
                  wWNPorts = wWNPorts :+ (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "fibreChannel" \ "domains" \ 0 \ "portWWN").get.toString()
                  wWNPorts = wWNPorts :+  (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "fibreChannel" \ "domains" \ 1 \ "portWWN").get.toString()
                } catch {
                  case e:Exception =>
                    mailBody = mailBody + s"<div style='float: left;'>&nbsp;- Fibre WWNs:&nbsp;</div> <div style='float: left; color: #ff0000;'>Insufficient fibre interfaces</div>"
                }
                var mDCProblemFound = false
                var mDCData: Array[String] = new Array[String](0)
                try {
                  mDCData = mDCData :+ (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "mdcPing" \ 0 \ "ipAddress").get.toString()
                  mDCData = mDCData :+ (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "mdcPing" \ 1 \ "ipAddress").get.toString()
                } catch {
                  case e:Exception =>
                    mDCProblemFound = true
                    mailBody = mailBody + s"<div style='float: left;'>&nbsp;- MDC Connectivity:&nbsp;</div> <div style='float: left; color: #ff0000;'>No data provided</div>"
                }
                var mDCDataTwo: Array[String] = new Array[String](0)
                if (!mDCProblemFound) {
                  try {
                    if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "mdcPing" \ 0 \ "visible").get.toString() == "true") {
                      mDCDataTwo = mDCDataTwo :+ "true"
                    }
                    if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "mdcPing" \ 1 \ "visible").get.toString() == "true") {
                      mDCDataTwo = mDCDataTwo :+ "true"
                    }
                  } catch {
                    case e:Exception =>
                      logger.debug(s"Could not read one of the visible values.")
                  }
                  if (mDCDataTwo.length == 0) {
                    mDCProblemFound = true
                    mailBody = mailBody + s"<div style='float: left;'>&nbsp;- MDC Connectivity:&nbsp;</div> <div style='float: left; color: #ff0000;'>No metadata controllers visible</div>"
                  }
                }
                if (!mDCProblemFound) {
                  if (mDCData.length != mDCDataTwo.length) {
                    mDCProblemFound = true
                    mailBody = mailBody + s"<div style='float: left;'>&nbsp;- MDC Connectivity:&nbsp;</div> <div style='float: left; color: #ff9000;'>Not all metadata controllers visible</div>"
                  }
                }
                if (!mDCProblemFound) {
                  var lossZero = 0
                  var lossOne = 0
                  try {
                    lossZero = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "mdcPing" \ 0 \ "packetloss").get.toString().toInt
                    lossOne = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "mdcPing" \ 1 \ "packetloss").get.toString().toInt
                  } catch {
                    case e:Exception =>
                      logger.debug(s"Could not read one of the packet loss values.")
                  }
                  if ((lossZero > 0) || (lossOne > 0)) {
                    mailBody = mailBody + s"<div style='float: left;'>&nbsp;- MDC Connectivity:&nbsp;</div> <div style='float: left; color: #ff9000;'>Packet loss seen</div>"
                  }
                }
                try {
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "denyDlcVolumes" ).get.toString() == "[\"true\"]") {
                    mailBody = mailBody + s"<div style='float: left;'>&nbsp;- UseDLC:&nbsp;</div> <div style='float: left; color: #ff0000;'>Expecting this value to be false</div>"
                  }
                } catch {
                  case e:Exception =>
                    mailBody = mailBody + s"<div style='float: left;'>&nbsp;- UseDLC:&nbsp;</div> <div style='float: left; color: #ff0000;'>No data provided</div>"
                }
                var driverData: Array[String] = new Array[String](0)
                try {
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 0 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 1 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 2 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 3 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 4 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 5 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 6 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                  if((responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "driverInfo" \ 7 \ "loaded").get.toString() == "true") {
                    driverData = driverData :+ "true"
                  }
                } catch {
                  case e:Exception =>
                    logger.debug(s"Could not read one of the driver values.")
                }
                if (driverData.length < 1) {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- Fibre drivers:&nbsp;</div> <div style='float: left; color: #ff0000;'>No drivers loaded</div>"
                }
                val iPAddresses = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "ipAddresses").get.toString()
                val iPAddressesArray = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "ipAddresses").get.toString().split(",")
                if (iPAddresses == "[]") {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- IP addresses:&nbsp;</div> <div style='float: left; color: #ff0000;'>No network connections detected</div>"
                } else if (iPAddressesArray.length < 2) {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- IP addresses:&nbsp;</div> <div style='float: left; color: #ff0000;'>No metadata network</div>"
                }
                val sANMounts = (responseObject \ "hits" \ "hits" \ 0 \ "_source" \ "sanMounts").get.toString()
                if (sANMounts == "[]") {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- SAN Mounts:&nbsp;</div> <div style='float: left; color: #ff0000;'>No data provided</div>"
                } else if ((!(sANMounts contains "Multimedia2")) || (!(sANMounts contains "Proxies2")) || (!(sANMounts contains "StudioPipe2"))) {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- SAN Mounts:&nbsp;</div> <div style='float: left; color: #ff0000;'>Expecting volumes Multimedia2, Proxies2, and StudioPipe2</div>"
                }
                mailBody = mailBody + s"</body></html>"
                if (isTriggerStatus(oldStatus, entryStatus)) {
                  logger.debug( s"About to attempt to send an e-mail to: ${playConfig.get[String]("mail.recipient_address")}")
                  try {
                    val email = Email( playConfig.get[String]("mail.subject"), s"${playConfig.get[String]("mail.sender_name")} <${playConfig.get[String]("mail.sender_address")}>", Seq(s"${playConfig.get[String]("mail.recipient_name")} <${playConfig.get[String]("mail.recipient_address")}>"), bodyHtml = Some(mailBody))
                    mailerClient.send(email)
                  } catch {
                    case e: Exception => logger.error(s"Sending e-mail failed with error: $e")
                  }
                }
            })
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
