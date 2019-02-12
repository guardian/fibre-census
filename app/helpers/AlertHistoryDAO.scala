package helpers

import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.{RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.http.update.UpdateResponse
import javax.inject.Inject
import models.AlertHistoryEntry
import play.api.{Configuration, Logger}
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AlertHistoryDAO @Inject() (config:Configuration, esClientMgr:ESClientManager) extends ZonedDateTimeEncoder with UUIDEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._
  private val logger = Logger(getClass)

  protected val indexName = config.get[String]("elasticsearch.alertsIndex")
  val client = esClientMgr.getClient()

  /**
    * returns all of the records, with most recently opened first, up to a given limit
    * @param lim Integer, maximum number of records to return
    * @return a Future with either a tuple of the records and total record count or an error
    */
  def getAll(lim:Int) = client.execute {
    search(indexName) sortByFieldDesc "openedAt" limit lim
  }.map(_.map(response=>(response.result.hits.hits.map(_.to[AlertHistoryEntry]).toSeq, response.result.hits.total)))

  /**
    * get alerts by hostname and subsystem identifiers
    * @param hostname hostname to search for
    * @param subsys subsystem identifier to search for
    * @param isOpen optional flag to search for open/closed alerts. Return all if this is None or not specified;
    *               if Some(false) return all closed alerts, if Some(true) return all open alerts
    * @return a Future containing either a RequestFailure or the SearchResponse from ES
    */
  def findAlerts(hostname:String, subsys:String, isOpen:Option[Boolean]=None) = {
    val initialQS = Seq(
      matchQuery("hostname",hostname),
      matchQuery("subsys",subsys),
    )

    val finalQS = isOpen match {
      case None=>initialQS
      case Some(value)=>
        if(value){  //searching for open alerts => there is no closed time
          initialQS ++ Seq(not(existsQuery("closedAt")))
        } else { //searching for closed alerts => there is a closed time
          initialQS ++ Seq(existsQuery("closedAt"))
        }
    }

    client.execute( {
      search(indexName) bool {
        must(finalQS)
      }
    }).map(_.map(_.result))
  }

  /**
    * saves the given entry to the database. Over-writes anything with the given ID.
    * @param entry entry to save
    * @return
    */
  def put(entry:AlertHistoryEntry) =
    client.execute({
      update(entry.alertId.toString).in(indexName).docAsUpsert(entry)
    })

  def alertForId(id:UUID) = {
    client.execute({
      get(id.toString).from(indexName)
    }).map(_.map(_.result.to[AlertHistoryEntry]))
  }

  /**
    * add an alert to the index. this method does NOT check for dupes. use addAlertNoDupe for that (which calls this method
    * internally)
    * @param hostname
    * @param subsys
    * @param description
    * @param openTime
    */
  def addAlert(hostname:String, subsys:String, description:String, withId:Option[UUID]=None, openTime:Option[ZonedDateTime]=None) = {
    val actualOpenTime = openTime match {
      case None=>ZonedDateTime.now()
      case Some(time)=>time
    }
    val idToUse = withId match {
      case None=>UUID.randomUUID()
      case Some(uuid)=>uuid
    }

    val rec = AlertHistoryEntry(idToUse,hostname, subsys, description, actualOpenTime, None)
    client.execute({
      update(idToUse.toString).in(s"$indexName").docAsUpsert(rec)
    })
  }

  /**
    * add an alert to the index; but don't duplicate. (use this one in external calls).
    * @param hostname hostname for the alert
    * @param subsys subsystem that has had a problem
    * @param description description of the alert
    * @param shouldUpdate Boolean; if true, then update an existing alert. If false, then return an error with 409 status code.
    * @param openTime optional, specify a time that the alert occurred. If none, then use the current time.
    * @return a Future with either a RequestFailure or a RequestSuccess
    */
  def addAlertNoDupe(hostname:String, subsys:String, description:String, shouldUpdate:Boolean, openTime:Option[ZonedDateTime]):Future[Either[RequestFailure, RequestSuccess[UpdateResponse]]] = {
    findAlerts(hostname, subsys).flatMap({
      case Right(response) =>
        if (response.hits.isEmpty) {
          addAlert(hostname, subsys, description, openTime = openTime)
        } else {
          if (shouldUpdate) {
            val existingRecord = response.hits.hits.head.to[AlertHistoryEntry]
            addAlert(hostname, subsys, description, withId = Some(existingRecord.alertId), openTime = openTime)
          } else {
            Future(Left(RequestFailure(409, Some("Record already existing"), Map(), null)))
          }
        }
      case Left(err)=>
        Future(Left(err))
    })
  }

  /**
    * marks the given ID as closed
    * @param alertId alert ID
    * @param closeTime optional, override closing time. If None or not set, then use current time
    * @return a Future, containing either a failure or an UpdateResponse success
    */
  def closeById(alertId:UUID, closeTime:Option[ZonedDateTime]):Future[Either[RequestFailure, RequestSuccess[UpdateResponse]]] = {
    alertForId(alertId).flatMap({
      case Right(entry)=>
        val actualCloseTime = closeTime match {
          case None=>ZonedDateTime.now()
          case Some(time)=>time
        }
        val updatedEntry = entry.copy(closedAt = Some(actualCloseTime))
        put(updatedEntry)
      case Left(err)=>
        Future(Left(err))
    })
  }

  def closeMatching(hostname:String, subsys:String, closeTime:Option[ZonedDateTime]) = {
    findAlerts(hostname, subsys, isOpen = Some(true)).flatMap({
      case Left(err)=>Future(Left(err))
      case Right(alertsList)=>
        val updateFutures = alertsList.hits.hits.map(hit=>{
          val alt = hit.to[AlertHistoryEntry]
          val actualCloseTime = closeTime match {
            case Some(time)=>time
            case None=>ZonedDateTime.now()
          }
          val updated = alt.copy(closedAt=Some(actualCloseTime))
          put(updated)
        }).toSeq

        val updates = Future.sequence(updateFutures)
        updates.map(results=>{
          val failures = results.collect({case Left(err)=>err})
          if(failures.nonEmpty){
            logger.error("Could not close all alerts: ")
            failures.foreach(err=>logger.error(err.toString))
            Left(failures.head)
          } else {
            logger.info(s"Closed ${results.length} open alerts")
            Right(s"Closed ${results.length} open alerts")
          }
        })
    })
  }
}
