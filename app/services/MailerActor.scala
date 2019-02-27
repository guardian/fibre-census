package services

import java.time.ZonedDateTime
import java.util.{Date, Properties}

import akka.actor.Actor
import javax.inject.Inject
import javax.mail._
import models.Email
import javax.mail.internet._
import play.api.{Configuration, Logger}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object MailerActor {

  /*public input messages*/
  case class SendEmail(toSend:Seq[Email])

  /*internal input messages*/
  protected case class InternalSend(msg:MimeMessage)
}

class MailerActor @Inject() (config:Configuration) extends Actor {
  import MailerActor._
  private val logger = Logger(getClass)

  protected val myRef = self

  def getMailerSession = {
    val properties = new Properties()
    properties.put("mail.smtp.host", config.get("email.smtpHost"))
    Session.getDefaultInstance(properties, null)
  }

  private val session = getMailerSession

  override def receive: Receive = {
    case InternalSend(msg)=>
      val maybeSmtpUser = config.getOptional[String]("email.smtpUser")
      val maybeSmtpPasswd = config.getOptional[String]("email.smtpPassword")

      val sendingResult = if(maybeSmtpPasswd.isDefined && maybeSmtpUser.isDefined) {
        Try {Transport.send(msg, maybeSmtpUser.get, maybeSmtpPasswd.get)}
      } else {
        Try {Transport.send(msg)}
      }

      sendingResult match {
        case Success(_)=>
          logger.info(s"Sent message ${msg.getSubject} successfully")
          sender() ! akka.actor.Status.Success
        case Failure(err)=>
          logger.error("Could not send message: ", err)
          sender() ! akka.actor.Status.Failure(err)
      }

    /**
      * dispatched by a caller to send a bunch of emails. We use a more scala-ish format to input,
      * so this converts that into the Java-ish format that javax expects and then dispatches InternalSend to
      * actually do the deed
      */
    case SendEmail(toSend)=>
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
        msgsToSend.foreach(msg=>myRef.tell(InternalSend(msg), sender()))
      } else {
        logger.error("No messages to send")
      }
  }
}
