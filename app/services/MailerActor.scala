package services

import java.time.ZonedDateTime
import java.util.{Date, Properties}

import akka.actor.Actor
import javax.inject.Inject
import javax.mail.{Address, Message, Session, Transport}
import models.Email
import javax.mail.internet._
import play.api.{Configuration, Logger}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object MailerActor {
  case class SendEmail(toSend:Seq[Email])
}

class MailerActor @Inject() (config:Configuration) extends Actor {
  import MailerActor._
  private val logger = Logger(getClass)

  override def receive: Receive = {
    case SendEmail(toSend)=>
      //set up an email session
      val properties = new Properties()
      properties.put("mail.smtp.host", config.get("email.smtpHost"))
      val session = Session.getDefaultInstance(properties, null)

      val maybeSmtpUser = config.getOptional[String]("email.smtpUser")
      val maybeSmtpPasswd = config.getOptional[String]("email.smtpPassword")
      val javaMsgList = toSend.map(myMsg=> Try {
        val msg = new MimeMessage(session)
        msg.setRecipients(Message.RecipientType.TO,myMsg.to.toArray.map(_.asInstanceOf[Address]))
        msg.setRecipients(Message.RecipientType.CC,myMsg.cc.toArray.map(_.asInstanceOf[Address]))
        msg.setRecipients(Message.RecipientType.BCC,myMsg.bcc.toArray.map(_.asInstanceOf[Address]))
        msg.setSubject(myMsg.subject, "UTF-8")
        msg.setText(myMsg.textBody, "UTF-8")
        msg.setSentDate(Date.from(myMsg.sendTime.getOrElse(ZonedDateTime.now()).toInstant))
        msg
      })

      val errors = javaMsgList.collect({case Failure(err)=>err})
      if(errors.nonEmpty){
        logger.error("The following messages refused to build:")
        errors.foreach(err=>logger.error("Message: ", err))
      }
      val msgsToSend = javaMsgList.collect({case Success(msg)=>msg})
      if(msgsToSend.nonEmpty){
        val sendResults = if(maybeSmtpPasswd.isDefined && maybeSmtpUser.isDefined) {
          msgsToSend.map(msg=>Try {Transport.send(msg, maybeSmtpUser.get, maybeSmtpPasswd.get)})
        } else {
          msgsToSend.map(msg=>Try {Transport.send(msg)})
        }
      } else {
        logger.error("No messages to send")
      }
  }
}
