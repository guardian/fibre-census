package models
import java.time.ZonedDateTime

import io.circe._
import io.circe.generic.auto

import scala.xml.NodeSeq

object HostInfo extends ((String,String,String,String,List[String],Option[FCInfo], Option[Seq[String]],Option[Seq[DriverInfo]], Option[Seq[MdcPing]], Option[Seq[SanMount]], ZonedDateTime,Option[String],Option[String],Option[String])=>HostInfo) {
  def fromXml(xml:NodeSeq, timestamp:ZonedDateTime):Either[Seq[String], HostInfo] = try {
    val fcInfos = if ((xml \ "fibrechannel").length==0){
      None
    } else {
      FCInfo.fromXml(xml \ "fibrechannel") match {
        case Left(err)=>throw new RuntimeException(err) //this gets picked up just below
        case Right(info)=>
          if(info.domains.isEmpty){
            None
          } else {
            Some(info)
          }
      }
    }
    val denyDlcVolumes = if((xml \ "denyDlc").length==0){
      None
    } else {
      Some((xml \ "denyDlc" \ "volume").map(_.text))
    }

    val driverInfo = if((xml \ "driverInfo").length==0){
      None
    } else {
      Some((xml \ "driverInfo" \ "driver").map(DriverInfo.fromXml(_)))
    }

    val pingInfos = (xml \ "mdcConnectivity" \ "mdc").map(node=>MdcPing.fromXml(node))

    val mountInfos = (xml \ "sanVolumesVisible" \ "mount").map(mountNode=>SanMount.fromXml(mountNode))

    val errors = pingInfos.collect({case Left(err)=>err}) ++ mountInfos.collect({case Left(err)=>err}) ++ driverInfo.getOrElse(Seq()).collect({case Left(err)=>err})

    val plutoHelperAgentInfo = if((xml \@ "plutoHelperAgentInfo").isEmpty){
      None
    } else {
      Some(xml \@ "plutoHelperAgentInfo")
    }

    val premiereProInfo = if((xml \@ "premiereProInfo").isEmpty){
      None
    } else {
      Some(xml \@ "premiereProInfo")
    }

    if(errors.nonEmpty){
      Left(errors)
    } else {
      Right(new HostInfo(xml \@ "hostname",
        xml \@ "computerName",
        xml \@ "model",
        xml \@ "hw_uuid",
        (xml \ "ipAddresses").map(_.text).toList,
        fcInfos,
        denyDlcVolumes,
        driverInfo.map(_.collect({ case Right(info) => info })),
        Some(pingInfos.collect({ case Right(info) => info })),
        Some(mountInfos.collect({ case Right(info) => info })),
        timestamp,
        plutoHelperAgentInfo,
        premiereProInfo,
        None))
    }
  } catch {
    case ex:Throwable=>
      Left(Seq(ex.toString))
  }
}

case class HostInfo(hostName:String, computerName:String, model:String, hwUUID:String, ipAddresses: List[String],
                    fibreChannel:Option[FCInfo], denyDlcVolumes:Option[Seq[String]], driverInfo:Option[Seq[DriverInfo]],
                    mdcPing:Option[Seq[MdcPing]], sanMounts:Option[Seq[SanMount]], lastUpdate:ZonedDateTime,
                    plutoHelperAgentInfo:Option[String], premiereProInfo:Option[String], status:Option[String])
