package models
import java.time.ZonedDateTime

import io.circe._
import io.circe.generic.auto

import scala.xml.NodeSeq

object HostInfo extends ((String,String,List[String],Option[FCInfo], ZonedDateTime)=>HostInfo) {
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
    Right(new HostInfo(xml \@ "hostname",xml \@ "computerName", (xml \ "ipAddresses").map(_.text).toList, fcInfos, timestamp))
  } catch {
    case ex:Throwable=>
      Left(ex.toString)
  }
}
case class HostInfo(hostName:String, computerName:String, ipAddresses: List[String], fibreChannel:Option[FCInfo], lastUpdate:ZonedDateTime)
