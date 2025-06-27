package services

import org.slf4j.LoggerFactory
import play.api.inject.guice.GuiceApplicationBuilder
import scopt.OptionParser
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object MailLauncher {
  private val logger = LoggerFactory.getLogger(getClass)

  case class Options()
  val parser = new OptionParser[Options]("mail-launcher") {
    head("mail-launcher", "1")
  }

  def main(args:Array[String]):Unit = {
    val app = new GuiceApplicationBuilder()
      .build()
    logger.info(s"Mail launcher run.")
    implicit val injector = app.injector
    val mailSender = injector.instanceOf(classOf[MailSender])

    mailSender.sendMail().onComplete({
      case Success(r) =>
        System.exit(0)
      case Failure(exception) =>
        logger.error(s"ERROR - Could not send e-mail: ${exception.getMessage}")
        System.exit(1)

    })
  }
}
