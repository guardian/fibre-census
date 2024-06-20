package controllers

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.sksamuel.elastic4s.http.ElasticDsl.update
import helpers.{ESClientManager, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import play.api.mvc.{AbstractController, ControllerComponents, PlayBodyParsers}
import models.{HostInfo, RecentLogin}
import responses.{GenericErrorResponse, ObjectListResponse, ErrorListResponse}
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.http.{DefaultHttpErrorHandler, HttpErrorHandler, ParserConfiguration}
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.circe.Circe
import play.api.libs.json.Json

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

@Singleton
class HostInfoController @Inject()(playConfig:Configuration,cc:ControllerComponents,
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

  def addRecord = Action.async(parse.xml) { request=>
    val client = esClientMgr.getClient()

    HostInfo.fromXml(request.body, ZonedDateTime.now()) match {
      case Right(entry)=>
        val idToUse = s"${entry.hostName}"

        client.execute {
            update(idToUse).in(s"$indexName/entry").docAsUpsert(entry)
        }.map({
          case Left(failure) => InternalServerError(GenericErrorResponse("elasticsearch_error", failure.error.toString).asJson)
          case Right(success) =>
            Ok(success.result.id)
        }).recoverWith({
          case ex:Throwable=>
            logger.error("Could not create host entry", ex)
            Future(InternalServerError(GenericErrorResponse("elasticsearch_error", ex.getLocalizedMessage).asJson))
        })
      case Left(err)=>
        Future(BadRequest(ErrorListResponse("bad_data", err).asJson))
    }
  }

  def simpleStringSearch(q:Option[String],start:Option[Int],length:Option[Int]) = Action.async {
    val cli = esClientMgr.getClient()

    val actualStart=start.getOrElse(0)
    val actualLength=length.getOrElse(50)

    q match {
      case Some(searchTerms) =>
        val searchString = s"*$searchTerms*"
        val responseFuture = cli.execute {
          search(indexName) query searchString from actualStart size actualLength
        }

        responseFuture.map({
          case Left(failure) =>
            InternalServerError(Json.obj("status" -> "error", "detail" -> failure.toString))
          case Right(results) =>
            val resultList = results.result.to[HostInfo] //using the HostInfoHitReader trait
            Ok(ObjectListResponse[IndexedSeq[HostInfo]]("ok","entry",resultList,results.result.totalHits.toInt).asJson)
        }).recover({
          case ex:Throwable=>
            logger.error("Could not process result from elastic: ", ex)
            InternalServerError(GenericErrorResponse("error",ex.toString).asJson)
        })
      case None => Future(BadRequest(GenericErrorResponse("error", "you must specify a query string with ?q={string}").asJson))
    }
  }
}
