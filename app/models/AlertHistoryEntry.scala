package models

import java.time.ZonedDateTime
import java.util.UUID

case class AlertHistoryEntry(alertId:UUID,hostname:String,subsys:String,description:String,openedAt:ZonedDateTime, closedAt:Option[ZonedDateTime])
