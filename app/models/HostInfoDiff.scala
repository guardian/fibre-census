package models

import java.time.ZonedDateTime
import io.circe.generic.auto._

case class HostInfoDiff (hostName:String,
                         computerName:Option[DiffPair[String]],
                         model:Option[DiffPair[String]],
                         hwUUID:Option[DiffPair[String]],
                         ipAddresses:Option[DiffPair[List[String]]],
                         fibreChannel:Option[DiffPair[Option[FCInfo]]],
                         denyDlcVolumes:Option[DiffPair[Option[Seq[String]]]],
                         driverInfo:Option[DiffPair[Option[Seq[DriverInfo]]]],
                         lastUpdate:ZonedDateTime) {

  def isEmpty = computerName.isEmpty && model.isEmpty && hwUUID.isEmpty && ipAddresses.isEmpty && fibreChannel.isEmpty && denyDlcVolumes.isEmpty && driverInfo.isEmpty
  def isDefined = ! isEmpty

}

object HostInfoDiff {
  def apply(first:HostInfo, second:HostInfo):HostInfoDiff = {
    new HostInfoDiff(
      first.hostName,
      DiffPair(first.computerName, second.computerName),
      DiffPair(first.model, second.model),
      DiffPair(first.hwUUID, second.hwUUID),
      DiffPair(first.ipAddresses, second.ipAddresses),
      DiffPair(first.fibreChannel, second.fibreChannel),
      DiffPair(first.denyDlcVolumes, second.denyDlcVolumes),
      DiffPair(first.driverInfo, second.driverInfo),
      first.lastUpdate
    )
  }
}