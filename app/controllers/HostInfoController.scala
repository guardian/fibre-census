package controllers

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import javax.inject.Inject
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents, PlayBodyParsers}
import models.HostInfo
import responses.GenericErrorResponse
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.http.{DefaultHttpErrorHandler, HttpErrorHandler, ParserConfiguration}
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.circe.Circe

class HostInfoController @Inject() (playConfig:Configuration,cc:ControllerComponents, defaultTempFileCreator:TemporaryFileCreator)(implicit system:ActorSystem)
  extends AbstractController(cc) with PlayBodyParsers with Circe {
  implicit def materializer:Materializer = ActorMaterializer()

  override def config:ParserConfiguration = {
    ParserConfiguration(2048*1024,10*1024*1024)
  }

  override def errorHandler:HttpErrorHandler = DefaultHttpErrorHandler

  override def temporaryFileCreator:TemporaryFileCreator = defaultTempFileCreator

  def addRecord = Action(parse.xml) { request=>
    HostInfo.fromXml(request.body, ZonedDateTime.now()) match {
      case Right(hostInfo)=>
        Ok("Read result")
      case Left(err)=>
        BadRequest(GenericErrorResponse("bad_data", err).asJson)
    }
  }
}
