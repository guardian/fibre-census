package models

import java.time.{Duration, LocalDateTime}

import scala.xml.NodeSeq
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.{ChronoField, ChronoUnit, TemporalAccessor, TemporalUnit}

object RecentLogin extends ((String, String, String, LocalDateTime, Duration)=>RecentLogin) {
  private val rubbishFormat = new DateTimeFormatterBuilder()
    .appendPattern("EE MMM d HH:mm:ss")
    .parseStrict()
    .parseDefaulting(ChronoField.YEAR, LocalDateTime.now().get(ChronoField.YEAR))
    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
    .toFormatter()

  def durationFromXmlObj(xml: NodeSeq):Duration = {
    val days = (xml \@ "days").toLong
    val hours = (xml \@ "hours").toLong
    val minutes = (xml \@ "minutes").toLong

    val totalSeconds = days*3600L*24L + hours *3600L + minutes*60L
    Duration.of(totalSeconds, ChronoUnit.SECONDS)
  }

  def fromXml(xml:NodeSeq):Either[String,RecentLogin] = try {
    val lastLoginTime = (xml \@ "login").replaceAll("  +", " ") + ":00"
    val parsed = rubbishFormat.parse(lastLoginTime)

    Right(new RecentLogin(
      xml \@ "hostname", xml \@ "username", xml \@ "location", LocalDateTime.from(parsed), durationFromXmlObj(xml \ "duration")
    ))
  } catch {
    case ex:Throwable=>
      Left(ex.toString)
  }
}

case class RecentLogin(hostname:String, username:String,location:String,loginTime:LocalDateTime,duration:Duration) {
  /**
    * generate a unique ID for this login for the index
    * @return
    */
  def idForElastic:String = {
    val encoder = java.util.Base64.getEncoder
    val rawString=s"$username@$hostname@${loginTime.format(DateTimeFormatter.ISO_DATE_TIME)}"
    encoder.encodeToString(rawString.toCharArray.map(_.toByte))
  }
}
