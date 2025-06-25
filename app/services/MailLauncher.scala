package services

import org.slf4j.LoggerFactory
import play.api.inject.guice.GuiceApplicationBuilder
import scopt.OptionParser
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object MailLauncher {
  private val logger = LoggerFactory.getLogger(getClass)

  case class Options(nukeInvalidBackups:Boolean, backupAll:Boolean, deleteAudioBackups:Boolean)
  val parser = new OptionParser[Options]("mail-launcher") {
    head("mail-launcher", "1")

    opt[Boolean]("nuke-invalid") action { (x,c)=>c.copy(nukeInvalidBackups = x)} text("instead of launching a backup, remove zero-length backup files")
    opt[Boolean]("all") action { (x,c)=>c.copy(backupAll = x)} text "try to back up every project instead of just 'in production'"
    opt[Boolean]("delete-audio") action { (x,c)=>c.copy(deleteAudioBackups = x)} text("Instead of launching a backup, remove audio backup files")
  }

  def main(args:Array[String]):Unit = {
    val app = new GuiceApplicationBuilder()
      .build()
    logger.info(s"Mail launcher run.")
    implicit val injector = app.injector
    val mailSender = injector.instanceOf(classOf[MailSender])

    mailSender.sendMail()

    mailSender.sendMail().onComplete({

      case Success(r) =>

        System.exit(0)
      case Failure(exception) =>
        logger.error(s"ERROR - Could not complete backup: ${exception.getMessage}")
        System.exit(1)

    })






    /*parser.parse(args, Options(false, false, false)) match {
      case Some(opts) =>

        if (opts.nukeInvalidBackups) {
          projectBackup.nukeInvalidBackups.onComplete({
            case Success(r) =>
              println(s"Nuked ${r.successCount} dodgy backups")
              System.exit(0)
            case Failure(exception) =>
              println(s"ERROR - Could not complete scan: ${exception.getMessage}")
              exception.printStackTrace()
              System.exit(1)
          })
        } else if (opts.deleteAudioBackups) {
          projectBackup.deleteAudioBackups.onComplete({
            case Success(r) =>
              println(s"Attempted to delete ${r.successCount} backups")
              System.exit(0)
            case Failure(exception) =>
              println(s"ERROR - Could not complete scan: ${exception.getMessage}")
              exception.printStackTrace()
              System.exit(1)
          })
        } else {
          projectBackup.backupProjects(!opts.backupAll).onComplete({
            case Success(r) =>
              logger.info(s"Out of ${r.totalCount} total, ${r.successCount} were backed up, ${r.failedCount} failed and ${r.notNeededCount} did not need backup")
              System.exit(0)
            case Failure(exception) =>
              logger.error(s"ERROR - Could not complete backup: ${exception.getMessage}")
              exception.printStackTrace()
              System.exit(1)
          })
        }
      case None =>
        System.exit(2)
    }*/
  }
}
