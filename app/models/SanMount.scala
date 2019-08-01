package models

import play.api.Logger
import scala.xml.NodeSeq

case class SanMount(mountPath:String, name:String, index:Int)

object SanMount extends ((String,String,Int)=>SanMount) {
  private val logger = Logger(getClass)

  def fromXml(node:NodeSeq):Either[String, SanMount] = try {
    Right(new SanMount(
      node \@ "mountPath",
      node \@ "name",
      (node \@ "number").toInt
    ))
  } catch {
    case ex:Throwable=>
      logger.error("Could not create SAN mount information from xml: ", ex)
      Left(ex.toString)
  }
}
