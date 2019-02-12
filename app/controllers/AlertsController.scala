package controllers

import java.util.UUID

import helpers.AlertHistoryDAO
import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.mvc.{AbstractController, ControllerComponents}
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.libs.circe.Circe
import responses.{GenericErrorResponse, ObjectListResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AlertsController @Inject() (config:Configuration, alertHistoryDAO: AlertHistoryDAO,cc:ControllerComponents)
  extends AbstractController(cc) with Circe {
  private val logger = Logger(getClass)

  def allAlerts(limit:Option[Int]) = Action.async {
    val actualLimit = limit.getOrElse(100)
    alertHistoryDAO.getAll(actualLimit).map({
      case Left(err)=>
        InternalServerError(GenericErrorResponse("db_error", err.toString).asJson)
      case Right((results, resultCount))=>
        Ok(ObjectListResponse("ok","alert",results, resultCount.toInt))
    })
  }

  def closeById(alertId:String) = Action.async {
    try {
      val actualId = UUID.fromString(alertId)
      alertHistoryDAO.closeById(actualId, None).map({
        case Left(err)=>
          InternalServerError(GenericErrorResponse("db_error", err.toString).asJson)
        case Right(result)=>
          Ok(GenericErrorResponse("ok",s"closed alert $alertId"))
      })
    } catch {
      case err:Throwable=>
        logger.error(s"Could not close for ID $alertId: ", err)
        Future(BadRequest(GenericErrorResponse("error",s"$alertId may not be a valid uuid")))
    }
  }
}
