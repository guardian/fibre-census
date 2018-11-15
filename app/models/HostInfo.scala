import java.time.ZonedDateTime
import com.outworkers.phantom.dsl._
import io.circe._
import io.circe.generic.auto

case class HostInfo(hostName:String, computerName:String, ipAddresses: List[String], fibreChannel:Option[FCInfo], lastUpdate:ZonedDateTime)

abstract class HostInfoRecord extends Table[HostInfoRecord,HostInfo] {
  object hostName extends StringColumn with PartitionKey
  object computerName extends StringColumn
  object ipAddresses extends ListColumn[String]
  object fibreChannel extends OptionalJsonColumn[FCInfo]
  object lastUpdate extends DateTimeColumn with ClusteringOrder with Descending
}