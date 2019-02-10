package services

import akka.actor.{Actor, ActorSystem}
import com.risksense.ipaddr.{IpAddress, IpNetwork}
import helpers.ZonedDateTimeEncoder
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import javax.inject.Inject
import models._
import play.api.{Configuration, Logger}
import scala.annotation.switch
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object AlertsActor {
  trait AAMsg

  /*
  "public" messages sent from elsewhere in the app
   */
  case class HostInfoUpdated(newHostInfo:HostInfo, oldHostInfo:Option[HostInfo]) extends AAMsg

  /*
  "private" messages sent by the actor
   */
  case class NewHostDiscovered(newHostInfo:HostInfo) extends AAMsg
  case class CheckParameterFcInfo(fieldName:String, diff:DiffPair[Option[FCInfo]])
  case class CheckParameterString(fieldName:String, diff:DiffPair[String])
  case class CheckParameterStringList(fieldName:String, diff:DiffPair[Seq[String]])
  case class CheckParameterOptStringList(fieldName:String, diff:DiffPair[Option[Seq[String]]])
  case class CheckParameterOptDriverInfoList(fieldName:String, diff:DiffPair[Option[Seq[DriverInfo]]])
  case class TriggerAlerts(alerts:Seq[ParameterAlert])

  /*
  reply messages sent back
   */
  case class ParameterInRange(fieldName:String) extends AAMsg
  case class ParameterAlert(fieldName:String, alertDesc:String) extends AAMsg
}

class AlertsActor @Inject() (system:ActorSystem, config:Configuration) extends Actor with ZonedDateTimeEncoder {
  private val logger = Logger(getClass)
  import AlertsActor._
  import akka.pattern.ask

  implicit val ec:ExecutionContext = system.dispatcher
  implicit val timeout:akka.util.Timeout = 60.seconds

  //this is over-ridden in testing to make it easier to spy on internal messages
  protected val ownRef = self

  override def receive: Receive = {
    /**
      * dispatched to check a string field
      */
    case CheckParameterString(fieldName, diff)=>
      logger.info("checkParameterString not implemented")
      sender() ! ParameterInRange(fieldName)

    /**
      * dispatched to check a string list
      */
    case CheckParameterStringList(fieldName,diff)=>
      (fieldName: @switch) match {
        case "ipAddresses"=>
          //check whether we have at least one address that is on the metadata network
          config.getOptional[String]("san.networkCIDR") match {
            case Some(nwCidr)=>
              val net = IpNetwork(nwCidr)
              val mdNetworkAddresses = diff.newValue.filter(ipString=>net.contains(ipString))
              if(mdNetworkAddresses.isEmpty){
                sender() ! ParameterAlert(fieldName, "No metadata network IP address detected")
              } else {
                logger.info(s"Found metadata IP address(es) $mdNetworkAddresses")
                sender() ! ParameterInRange(fieldName)
              }
            case None=>
              logger.warn("Cannot check IP address change, need to set san.networkCIDR in the configuration")
              sender() ! ParameterInRange(fieldName)
          }
        case "denyDlcVolumes"=>
          //check whether the denyDLC count has dropped
          if(diff.newValue.length<diff.oldValue.length){
            val dropCount = diff.oldValue.length - diff.newValue.length
            val droppedDenyDlc = diff.oldValue.filter(entry=> !diff.newValue.contains(entry))
            val msg = s"Dropped $dropCount denyDLC entries: $droppedDenyDlc"
            logger.info(msg)
            sender() ! ParameterAlert(fieldName, msg)
          } else if(diff.newValue.length<diff.oldValue.length){
            val addCount = diff.newValue.length - diff.oldValue.length
            logger.info(s"Added $addCount new denyDLC entries")
            sender() ! ParameterInRange(fieldName)
          } else {
            logger.info("No change to denyDLC count")
            sender() ! ParameterInRange(fieldName)
          }
        case _=>
          logger.warn(s"Nothing to handle check for field $fieldName")
          sender() ! ParameterInRange(fieldName)
      }

    /**
      * dispatched to check an Optional string list.  This raises an alert if the new value is not defined when the old
      * value is, otherwise delegates to CheckParameterStringList if both old and new values are defined.
      */
    case CheckParameterOptStringList(fieldName, diff)=>
      if(diff.oldValue.isDefined && diff.newValue.isEmpty){
        logger.info(s"String information for $fieldName has gone")
        sender() ! ParameterAlert(fieldName, "Information has disappeared")
      } else if(diff.oldValue.isDefined && diff.newValue.isDefined){
        //if we have two values, then the check is the same as for a mandatory string list
        ownRef.tell(CheckParameterStringList(fieldName, new DiffPair(diff.newValue.get, diff.oldValue.get)), sender())
      }

    /**
      * dispatched to check an optional DriverInfo field.  There's only one of these.
      */
    case CheckParameterOptDriverInfoList(fieldName, diff)=>
      if(diff.oldValue.isDefined && diff.newValue.isEmpty) {
        logger.info(s"Driver information for $fieldName has gone")
        sender() ! ParameterAlert(fieldName, "Driver information has disappeared")
      }else if(diff.oldValue.isDefined && diff.newValue.isDefined){
        val oldDriverInfo = diff.oldValue.get
        val newDriverInfo = diff.newValue.get

        //placeholder, do something with the driver info check here
        sender() ! ParameterInRange(fieldName)
      }

    /**
      * dispatched to check a FCInfo field. There's only one of these (the fibrechannel info, funnily enough...)
      */
    case CheckParameterFcInfo(fieldName, diff)=>
      logger.debug("FCInfo changed")
      if(diff.oldValue.isDefined && diff.newValue.isEmpty){
        logger.info("Fibrechannel information has gone")
        sender() ! ParameterAlert(fieldName, "Fibrechannel information disappeared")
      } else if(diff.oldValue.isDefined && diff.newValue.isDefined){
        val oldFC = diff.oldValue.get
        val newFC = diff.newValue.get

        val matchedDomains = FCDomain.matchup(oldFC.domains, newFC.domains)
        val concerns = matchedDomains.map(result=>{
          if(result._2.isEmpty){
            Some(s"Fibrechannel domain ${result._1.name} disappeared")
          } else if(result._1.lunCount> result._2.get.lunCount){
            Some(s"Fibrechannel domain ${result._1.name} lost LUNs, from ${result._1.lunCount} to ${result._2.get.lunCount}")
          } else if(result._1.status.contains("Link Established") && ! result._2.get.status.contains("Link Established")){
            Some(s"Fibrechannel domain ${result._1.name} lost link, status went to ${result._2.get.status}")
          }
        }).collect({
          case Some(concern)=>concern
        })

        if(concerns.nonEmpty){
          logger.info(s"Found fibrechannel concerns: $concerns")
          sender() ! ParameterAlert(fieldName, concerns.mkString("\n"))
        } else {
          logger.info("Found no fibrechannel concerns")
          sender() ! ParameterInRange(fieldName)
        }
      }

    case HostInfoUpdated(newHostInfo, maybeOldHostInfo)=>
      logger.info(s"Received new host information: $newHostInfo")
      maybeOldHostInfo match {
        case None=>ownRef ! NewHostDiscovered(newHostInfo)
        case Some(oldHostInfo)=>
          val diffs = HostInfoDiff(newHostInfo, oldHostInfo)
          logger.debug(s"Received information $diffs")

          val params = HostInfoDiff.unapply(diffs).get
          val msgSeq = Seq(
            params._2.map(p=>CheckParameterString("computerName",p)),
            params._3.map(p=>CheckParameterString("model", p)),
            params._4.map(p=>CheckParameterString("hwUUID", p)),
            params._5.map(p=>CheckParameterStringList("ipAddresses",p)),
            params._6.map(p=>CheckParameterFcInfo("fibreChannel", p)),
            params._7.map(p=>CheckParameterOptStringList("denyDlcVolumes", p)),
            params._8.map(p=>CheckParameterOptDriverInfoList("driverInfo", p))
          ).collect({case Some(msg)=>msg})

          if(msgSeq.isEmpty){
            logger.info("No changes to worry about")
          } else {
            val alertsFuture = Future.sequence(msgSeq.map(msg=>ownRef ? msg))
              .map(_.map(_.asInstanceOf[AAMsg])
                  .collect({
                    case a:ParameterAlert=>a
                  })
              )

            alertsFuture.onComplete({
              case Failure(err)=>logger.error(s"Could not determine alerts for ${newHostInfo.hostName}", err)
              case Success(alerts)=>
                if(alerts.nonEmpty){
                  logger.info(s"Found alerts for ${newHostInfo.hostName}: $alerts, triggering")
                  ownRef ! TriggerAlerts(alerts)
                } else {
                  logger.info(s"No alerts found for ${newHostInfo.hostName}")
                }
            })
          }
      }
  }
}
