package controllers

import java.io.File
import java.security.MessageDigest

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{FileIO, Keep, Sink}
import javax.inject._
import play.api.Logger
import play.api.mvc._

import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, actorSystem:ActorSystem) extends AbstractController(cc) {
  private val logger=Logger(getClass)
  implicit val mat:Materializer = ActorMaterializer.create(actorSystem)

  def getCacheBustingString = {
    val digester = MessageDigest.getInstance("md5")
    val src = FileIO.fromPath(new File("public/javascripts/bundle.js").toPath,100,0L)

    val materializedFlow = src.toMat(Sink.foreach(elem=>digester.update(elem.toByteBuffer)))(Keep.left).run()
    materializedFlow.map(result=>{
      if(result.wasSuccessful){
        logger.debug("Successfully checksummed javascript")
        Some(digester.digest().map("%02x".format(_)).mkString)
      } else {
        logger.warn(s"Could not checksum javascript: ${result.getError}")
        None
      }
    })
  }

  private val maybeCacheBustingString = getCacheBustingString

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    val cbString = if(maybeCacheBustingString.isCompleted){
      maybeCacheBustingString.value match {
        case Some(Success(Some(value)))=>value
        case Some(Success(None))=>"xxxx"
        case Some(Failure(err))=>
          logger.error("Could not calculate cachebusting: ", err)
          "yyyy"
        case None=>
          logger.error("Cachebusting calculation worked but no result")
          "zzzz"
      }
    } else {
      "nnnnn"
    }

    Ok(views.html.index("Fibre Census")(cbString))
  }

  def healthcheck = Action {
    Ok("ok")
  }

}
