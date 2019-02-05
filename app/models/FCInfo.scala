package models
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode

import scala.xml.{Elem, NodeSeq}

object FCDomain extends ((String,Option[String],Option[String], Option[String],Int)=>FCDomain) {
  private def getAttrOption(xml:NodeSeq, attr:String) = {
    val raw = xml \@ attr
    if(raw==""){
      None
    } else {
      Some(raw)
    }
  }

  def fromXml(xml:NodeSeq):Either[String, FCDomain] = try {
    if(!xml.head.label.startsWith("Domain")) throw new RuntimeException(s"expected a Domain line, got ${xml.head.label}")
    Right(new FCDomain(
      xml.head.label,
      getAttrOption(xml,"Speed"),
      getAttrOption(xml,"Status"),
      getAttrOption(xml,"WWN"),
      (xml \@ "lunCount").toInt
    ))
  } catch {
    case ex:Throwable=>
      Left(ex.toString)
  }

  /**
    * matches corresponding FC domains in `firstSeq` to the same one with corresponding name in `secondSeq`.
    * if no corresponding domain exists in `secondSeq`, then None is returned as the corresponding value
    * if no corresponding domain exists in `firstSeq`, then the value is not included in the return value
    * @param firstSeq first sequence of FCDomain to check
    * @param secondSeq second sequence of FCDomain to check
    * @return a Sequence of 2-tuples, same length as `firstSeq`. First element is the domain from firstSeq, second element is
    *         either the corresponding element from secondSeq or None.
    */
  def matchup(firstSeq:Seq[FCDomain], secondSeq:Seq[FCDomain]):Seq[Tuple2[FCDomain,Option[FCDomain]]] =
    firstSeq.map(dom_1=>Tuple2(dom_1, secondSeq.find(dom_2=>dom_2.name==dom_1.name)))

}

case class FCDomain(name:String, speed:Option[String],status:Option[String], portWWN:Option[String], lunCount:Int)

object FCInfo extends((Seq[FCDomain], String)=>FCInfo) {
  def fromXml(xml:NodeSeq):Either[String,FCInfo] = try {
    val domains = xml.head.child.map({
      case e: Elem =>
        if(e.label.startsWith("Domain")){
          Some(FCDomain.fromXml(e))
        } else {
          None
        }
      case _=>
        None
    }).collect({
      case Some(dom)=>dom
    })

    val failedDoms = domains.collect({case Left(err)=>err})
    if(failedDoms.nonEmpty){
      Left(s"Could not parse FC domains: $failedDoms")
    } else {
      Right(new FCInfo(domains.collect({case Right(dom)=>dom}), xml \@"Product"))
    }
  } catch {
    case ex:Throwable=>
      Left(ex.toString)
  }
}

case class FCInfo(domains: Seq[FCDomain], productName: String)