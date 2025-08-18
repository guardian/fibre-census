package services

import helpers.ESClientManager
import org.slf4j.LoggerFactory
import play.api.Configuration
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer._

@Singleton
class MailSender @Inject()(playConfig:Configuration, esClientMgr:ESClientManager)
                          (implicit mailerClient: MailerClient){
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
        var warningData: Array[String] = new Array[String](0)
        var problemData: Array[String] = new Array[String](0)
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
            case e:Exception =>
              logger.debug(s"Could not get status.")
              status = ""
          }

          val sourceObject = (record \ "_source")

          if (status == "warning") {
            warningHosts = warningHosts :+ hostName
            warningData = warningData :+ sourceObject.get.toString().replace("JsDefined(", "")
          }
          if (status == "problem") {
            problemHosts = problemHosts :+ hostName
            problemData = problemData :+ sourceObject.get.toString().replace("JsDefined(", "")
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
          var mailBody = s"<html><body>"

          if (problemHosts.length > 0) {
            mailBody = mailBody + s"<br /> <div style='color: #ff0000;'>The following machines have the status 'problem': -</div>"
            var problemPlace = 0
            for (hostNameString <- problemHosts) {
              logger.debug(problemData(problemPlace))
              mailBody = mailBody + s"<div style='float: left;'>$hostNameString</div>"
              val responseObjectTwo = Json.parse(problemData(problemPlace))
              val lUNZero = (responseObjectTwo \ "fibreChannel" \ "domains" \ 0 \ "lunCount")
              val lUNOne = (responseObjectTwo \ "fibreChannel" \ "domains" \ 1 \ "lunCount")
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
                wWNPorts = wWNPorts :+ (responseObjectTwo \ "fibreChannel" \ "domains" \ 0 \ "portWWN").get.toString()
                wWNPorts = wWNPorts :+  (responseObjectTwo \ "fibreChannel" \ "domains" \ 1 \ "portWWN").get.toString()
              } catch {
                case e:Exception =>
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- Fibre WWNs:&nbsp;</div> <div style='float: left; color: #ff0000;'>Insufficient fibre interfaces</div>"
              }
              var mDCProblemFound = false
              var mDCData: Array[String] = new Array[String](0)
              try {
                mDCData = mDCData :+ (responseObjectTwo \ "mdcPing" \ 0 \ "ipAddress").get.toString()
                mDCData = mDCData :+ (responseObjectTwo \ "mdcPing" \ 1 \ "ipAddress").get.toString()
              } catch {
                case e:Exception =>
                  mDCProblemFound = true
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- MDC Connectivity:&nbsp;</div> <div style='float: left; color: #ff0000;'>No data provided</div>"
              }
              var mDCDataTwo: Array[String] = new Array[String](0)
              if (!mDCProblemFound) {
                try {
                  if((responseObjectTwo \ "mdcPing" \ 0 \ "visible").get.toString() == "true") {
                    mDCDataTwo = mDCDataTwo :+ "true"
                  }
                  if((responseObjectTwo \ "mdcPing" \ 1 \ "visible").get.toString() == "true") {
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
                  lossZero = (responseObjectTwo \ "mdcPing" \ 0 \ "packetloss").get.toString().toInt
                  lossOne = (responseObjectTwo \ "mdcPing" \ 1 \ "packetloss").get.toString().toInt
                } catch {
                  case e:Exception =>
                    logger.debug(s"Could not read one of the packet loss values.")
                }
                if ((lossZero > 0) || (lossOne > 0)) {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- MDC Connectivity:&nbsp;</div> <div style='float: left; color: #ff9000;'>Packet loss seen</div>"
                }
              }
              try {
                if((responseObjectTwo \ "denyDlcVolumes" ).get.toString() == "[\"true\"]") {
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- UseDLC:&nbsp;</div> <div style='float: left; color: #ff0000;'>Expecting this value to be false</div>"
                }
              } catch {
                case e:Exception =>
                  mailBody = mailBody + s"<div style='float: left;'>&nbsp;- UseDLC:&nbsp;</div> <div style='float: left; color: #ff0000;'>No data provided</div>"
              }
              var driverData: Array[String] = new Array[String](0)
              try {
                if((responseObjectTwo \ "driverInfo" \ 0 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
                if((responseObjectTwo \ "driverInfo" \ 1 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
                if((responseObjectTwo \ "driverInfo" \ 2 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
                if((responseObjectTwo \ "driverInfo" \ 3 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
                if((responseObjectTwo \ "driverInfo" \ 4 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
                if((responseObjectTwo \ "driverInfo" \ 5 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
                if((responseObjectTwo \ "driverInfo" \ 6 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
                if((responseObjectTwo \ "driverInfo" \ 7 \ "loaded").get.toString() == "true") {
                  driverData = driverData :+ "true"
                }
              } catch {
                case e:Exception =>
                  logger.debug(s"Could not read one of the driver values.")
              }
              if (driverData.length < 1) {
                mailBody = mailBody + s"<div style='float: left;'>&nbsp;- Fibre drivers:&nbsp;</div> <div style='float: left; color: #ff0000;'>No drivers loaded</div>"
              }
              mailBody = mailBody + s" <br />"
              problemPlace = problemPlace + 1
            }
          }
          if (warningHosts.length > 0) {
            mailBody = mailBody + s"<br /> <div style='color: #ff9000;'>The following machines have the status 'warning': -</div>"
            for (hostNameString <- warningHosts) {
              mailBody = mailBody + s"$hostNameString <br />"
            }
          }

          mailBody = mailBody + s"</body></html>"

          logger.info( s"About to attempt to send an e-mail to: ${playConfig.get[String]("mail.recipient_address")}")
          try {
            val email = Email( playConfig.get[String]("mail.summary_subject"), s"${playConfig.get[String]("mail.sender_name")} <${playConfig.get[String]("mail.sender_address")}>", Seq(s"${playConfig.get[String]("mail.recipient_name")} <${playConfig.get[String]("mail.recipient_address")}>"), bodyHtml = Some(mailBody))
            mailerClient.send(email)
          } catch {
            case e: Exception => logger.error(s"Sending e-mail failed with error: $e")
          }
        }
    })
  }
}

