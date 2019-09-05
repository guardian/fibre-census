package controllers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import helpers.{ESClientManager, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, RequestHeader}
import responses.GenericErrorResponse
import io.circe.syntax._
import io.circe.generic.auto._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


@Singleton
class DeleteController @Inject()(playConfig:Configuration,cc:ControllerComponents,
                                       defaultTempFileCreator:TemporaryFileCreator, esClientMgr:ESClientManager)(implicit system:ActorSystem)
  extends AbstractController(cc) with ZonedDateTimeEncoder with Circe {

  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._



  private val logger = Logger(getClass)
  implicit def materializer:Materializer = ActorMaterializer()

  private val client = esClientMgr.getClient()

  protected val indexName = playConfig.get[String]("elasticsearch.indexName")
  protected val loginsIndex = playConfig.get[String]("elasticsearch.loginsIndexName")


  def deleteComputerRecord(hostName:String) = client.execute {
    deleteById(indexName, "entry", hostName)
  }

  def deleteUserRecordsForComputer(hostName:String) = client.execute {
    deleteByQuery(loginsIndex, "login", matchQuery("hostname.keyword",hostName))
  }

  def deleteFor(hostName:String) = Action.async {
    Future.sequence(
      Seq(
        deleteComputerRecord(hostName),
        deleteUserRecordsForComputer(hostName)
      )).map(results=>{
        val failures=results.collect({case Left(err)=>err})
        if(failures.nonEmpty){
          failures.foreach(f=>logger.error(f.toString))
          InternalServerError(GenericErrorResponse("db_error",failures.map(_.toString).mkString(",")).asJson)
        } else {
          Ok(GenericErrorResponse("ok", s"$hostName records deleted").asJson)
        }
      }
    )
  }
}
