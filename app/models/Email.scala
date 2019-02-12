package models

import java.time.ZonedDateTime

import javax.mail.internet.InternetAddress

case class Email (to:Seq[InternetAddress], cc:Seq[InternetAddress], bcc:Seq[InternetAddress], subject:String, textBody:String, sendTime:Option[ZonedDateTime])