package controllers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import helpers.{ESClientManager, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import models.RecentLogin
import play.api.{Configuration, Logger}
import play.api.http.{DefaultHttpErrorHandler, HttpErrorHandler, ParserConfiguration}
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, PlayBodyParsers}
import responses.{GenericErrorResponse, ObjectListResponse}
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class LoginHistoryController @Inject()(playConfig:Configuration,cc:ControllerComponents,
                                       defaultTempFileCreator:TemporaryFileCreator, esClientMgr:ESClientManager)(implicit system:ActorSystem)
  extends AbstractController(cc) with PlayBodyParsers with ZonedDateTimeEncoder with Circe {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._

  private val logger = Logger(getClass)
  implicit def materializer:Materializer = ActorMaterializer()


  protected val indexName = playConfig.get[String]("elasticsearch.indexName")
  protected val loginsIndex = playConfig.get[String]("elasticsearch.loginsIndexName")
  override def config:ParserConfiguration = {
    ParserConfiguration(2048*1024,10*1024*1024)
  }

  override def errorHandler:HttpErrorHandler = DefaultHttpErrorHandler

  override def temporaryFileCreator:TemporaryFileCreator = defaultTempFileCreator

  def updateLogins = Action.async(parse.xml) { request=>
    val client = esClientMgr.getClient()
    val maybeLoginsList = (request.body \ "recentLogins").map(RecentLogin.fromXml(_))

    val errors = maybeLoginsList.collect({
      case Left(err)=>err
    })

    if(errors.nonEmpty){
      Future(BadRequest(GenericErrorResponse("bad_data", errors.mkString(";")).asJson))
    } else {
      val loginsList = maybeLoginsList.collect({
        case Right(log)=>log
      })
      val resulFutures = Future.traverse(loginsList) { loginEntry=>
        client.execute {
          update(loginEntry.idForElastic).in(s"$loginsIndex/login").docAsUpsert(loginEntry)
        }
      }
      resulFutures.map(results=>{
        val outErrors = results.collect({case Left(err)=>err})
        if(outErrors.nonEmpty){
          InternalServerError(GenericErrorResponse("index_error", outErrors.mkString(";")).asJson)
        } else {
          Ok(GenericErrorResponse("ok", s"${results.length} records stored").asJson)
        }
      })
    }
  }

  def loginsFor(hostName:String, limit:Option[Int]) = Action.async {
    val client = esClientMgr.getClient()

    val actualLimit = limit match {
      case Some(lim)=>lim
      case None=>10
    }

    val resultFuture = client.execute {
      search(s"$loginsIndex/login") query {
        termQuery("hostname", hostName)
      } sortByFieldDesc "loginTime" limit actualLimit
    }

    resultFuture.map({
      case Left(err)=>InternalServerError(GenericErrorResponse("search_error", err.toString).asJson)
      case Right(results)=>Ok(ObjectListResponse("ok","login_history", results.result.to[RecentLogin], results.result.totalHits.toInt).asJson)
    })
  }
}
