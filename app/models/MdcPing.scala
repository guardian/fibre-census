package models

import scala.xml.NodeSeq

/*
  <mdcConnectivity>
    <mdc ip="192.168.22.1" number="1" packetloss="0" ping="true" />
    <mdc ip="192.168.22.3" number="2" packetloss="0" ping="true" />
  </mdcConnectivity>
 */
case class MdcPing(ipAddress: String, packetloss:Int, visible:Boolean)

object MdcPing extends ((String,Int,Boolean)=>MdcPing) with stringToBool {
  def fromXml(node:NodeSeq):Either[String,MdcPing] = try {
    Right(new MdcPing(
      node \@ "ip",
      (node \@ "packetloss").toInt,
      stringToBool(node \@ "ping")
    ))
  } catch {
    case ex:Throwable=>Left(ex.toString)
  }
}