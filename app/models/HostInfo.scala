package models
import java.time.ZonedDateTime

import io.circe._
import io.circe.generic.auto

import scala.xml.NodeSeq

object HostInfo extends ((String,String,String,String,List[String],Option[FCInfo], Option[Seq[String]],Option[Seq[DriverInfo]], ZonedDateTime)=>HostInfo) {
  def fromXml(xml:NodeSeq, timestamp:ZonedDateTime):Either[String, HostInfo] = try {
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
      Some((xml \ "driverInfo" \ "driver").map(DriverInfo.fromXml(_)).collect({case Right(info)=>info}))
    }

    Right(new HostInfo(xml \@ "hostname",xml \@ "computerName", xml \@ "model", xml \@ "hw_uuid",
      (xml \ "ipAddresses").map(_.text).toList, fcInfos, denyDlcVolumes, driverInfo, timestamp))
  } catch {
    case ex:Throwable=>
      Left(ex.toString)
  }
}

case class HostInfo(hostName:String, computerName:String, model:String, hwUUID:String, ipAddresses: List[String], fibreChannel:Option[FCInfo], denyDlcVolumes:Option[Seq[String]], driverInfo:Option[Seq[DriverInfo]], lastUpdate:ZonedDateTime)
