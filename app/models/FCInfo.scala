import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import com.outworkers.phantom.dsl._
import io.circe.parser.decode

object FCInfo extends ((List[FCDomain], String)=>FCInfo) {
  implicit val jsonPrimitive:Primitive[FCInfo] = {
    Primitive.json[FCInfo](_.asJson.noSpaces)(decode[FCInfo](_).right.get)
  }
}

case class FCDomain(name:String, speed:Option[String],status:Option[String], portWWN:Option[String], lunCount:Int)
case class FCInfo(domains: List[FCDomain], productName: String)