import java.time.ZonedDateTime

import akka.actor.Props
import models.{DiffPair, FCDomain, FCInfo, HostInfo}
import org.specs2.mutable.Specification
import io.circe.generic.auto._
import akka.pattern.ask
import akka.testkit.TestProbe
import com.sksamuel.elastic4s.http.{RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.http.update.UpdateResponse
import helpers.AlertHistoryDAO
import org.specs2.mock.Mockito
import play.api.Configuration
import services.AlertsActor

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class AlertsActorSpec extends Specification with Mockito {
  sequential
  implicit val timeout:akka.util.Timeout = 10.seconds
  val blankConfig = Configuration.empty
  
  import services.AlertsActor._

  val mockDAO = mock[AlertHistoryDAO]
  
  "AlertsActor!CheckParameter[FCInfo]" should {
    "reply with ParameterInRange if the diffs are the same" in new AkkaTestkitSpecs2Support {
      val mockFcInfo = FCInfo(
        Seq(
          FCDomain(
            "domain_1",
            Some("8G"),
            Some("Link Established"),
            Some("FakeWWN"),
            10
          )
        ),
        "product name"
      )

      val mockFcInfoAfter = FCInfo(
        Seq(
          FCDomain(
            "domain_1",
            Some("16G"),
            Some("Link Established"),
            Some("FakeWWN"),
            10
          ),
          FCDomain(
            "domain_2",
            None,
            None,
            None,
            0
          )
        ),
        "product name"
      )

      val d = DiffPair(Some(mockFcInfoAfter), Some(mockFcInfo))
      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO)))

      val result = Await.result(toTest ? CheckParameterFcInfo("test-field",d.get), 10.seconds)
      result.asInstanceOf[AnyRef] must beAnInstanceOf[ParameterInRange]
    }

    "reply with ParameterAlert if the LUN count drops" in new AkkaTestkitSpecs2Support {
      val mockFcInfo = FCInfo(
        Seq(
          FCDomain(
            "domain_1",
            Some("8G"),
            Some("Link Established"),
            Some("FakeWWN"),
            20
          )
        ),
        "product name"
      )

      val mockFcInfoAfter = FCInfo(
        Seq(
          FCDomain(
            "domain_1",
            Some("16G"),
            Some("Link Established"),
            Some("FakeWWN"),
            10
          )
        ),
        "product name"
      )

      val d = DiffPair(Some(mockFcInfoAfter), Some(mockFcInfo))
      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO)))

      val result = Await.result(toTest ? CheckParameterFcInfo("test-field",d.get), 10.seconds)
      result.asInstanceOf[AnyRef] must beAnInstanceOf[ParameterAlert]
    }
  }

  "AlertsActor!CheckParameter[ipAddresses]" should {
    "alert if the new address is not within a given cidr" in  new AkkaTestkitSpecs2Support {
      val d = DiffPair(List("169.254.0.3"),List("169.254.0.3","192.168.1.3"))
      val config = Configuration.from(Map("san.networkCIDR"->"192.168.1.0/24"))

      val toTest = system.actorOf(Props(new AlertsActor(system, config, mockDAO)))

      val result = Await.result(toTest ? CheckParameterStringList("ipAddresses",d.get), 10.seconds)
      result.asInstanceOf[AnyRef] must beAnInstanceOf[ParameterAlert]
    }

    "not alert if the new address is not within a given cidr" in  new AkkaTestkitSpecs2Support {
      val d = DiffPair(List("169.254.0.3","192.168.1.5"),List("169.254.0.3","192.168.1.3"))
      val config = Configuration.from(Map("san.networkCIDR"->"192.168.1.0/24"))

      val toTest = system.actorOf(Props(new AlertsActor(system, config, mockDAO)))

      val result = Await.result(toTest ? CheckParameterStringList("ipAddresses",d.get), 10.seconds)
      result.asInstanceOf[AnyRef] must beAnInstanceOf[ParameterInRange]
    }
  }

  "AlertsActor!CheckParameter[denyDLCVolumes]" should {
    "alert if the number of denied volumes drops" in  new AkkaTestkitSpecs2Support {
      val d = DiffPair(List("vol1"),List("vol1","vol2"))

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO)))

      val result = Await.result(toTest ? CheckParameterStringList("denyDlcVolumes",d.get), 10.seconds)
      result.asInstanceOf[AnyRef] must beAnInstanceOf[ParameterAlert]
    }

    "not alert if the number of denied volumes increases" in  new AkkaTestkitSpecs2Support {
      val d = DiffPair(List("vol1","vol2"),List("vol1"))

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO)))

      val result = Await.result(toTest ? CheckParameterStringList("denyDlcVolumes",d.get), 10.seconds)
      result.asInstanceOf[AnyRef] must beAnInstanceOf[ParameterInRange]
    }
  }

  "AlertsActor!HostInfoUpdated" should {
    "dispatch NewHostDiscovered if old host info is absent" in new AkkaTestkitSpecs2Support {
      val mockHostInfo = HostInfo(
        "fake_host",
        "fake_computer_name",
        "fake_model",
        "fake_uuid",
        List(),
        None,
        None,
        None,
        ZonedDateTime.now()
      )
      val testProbe = TestProbe()

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO){
        override protected val ownRef = testProbe.ref
      }))

      toTest ! HostInfoUpdated(mockHostInfo, None)

      testProbe.expectMsg(5.seconds, NewHostDiscovered(mockHostInfo))
    }

    "not dispatch any messages if old and new host info are the same" in new AkkaTestkitSpecs2Support {
      val mockHostInfo = HostInfo(
        "fake_host",
        "fake_computer_name",
        "fake_model",
        "fake_uuid",
        List(),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val mockHostInfoOld = HostInfo(
        "fake_host",
        "fake_computer_name",
        "fake_model",
        "fake_uuid",
        List(),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val testProbe = TestProbe()

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO){
        override protected val ownRef = testProbe.ref
      }))

      toTest ! HostInfoUpdated(mockHostInfo, Some(mockHostInfoOld))

      testProbe.expectNoMessage(5.seconds)
    }

    "not dispatch messages for any differing fields" in new AkkaTestkitSpecs2Support {
      val mockHostInfo = HostInfo(
        "fake_host",
        "fake_computer_name",
        "fake_model",
        "fake_uuid",
        List(),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val mockHostInfoOld = HostInfo(
        "fake_host",
        "fake_computer_name",
        "fake_model",
        "fake_uuid",
        List(),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val testProbe = TestProbe()

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO){
        override protected val ownRef = testProbe.ref
      }))

      toTest ! HostInfoUpdated(mockHostInfo, Some(mockHostInfoOld))

      testProbe.expectNoMessage(5.seconds)
    }

    "dispatch message for any differing fields and stop if they are all in range" in new AkkaTestkitSpecs2Support {
      val mockHostInfo = HostInfo(
        "fake_host",
        "fake_computer_name_updated",
        "fake_model",
        "fake_uuid",
        List("new_ip_address"),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val mockHostInfoOld = HostInfo(
        "fake_host",
        "fake_computer_name",
        "fake_model",
        "fake_uuid",
        List(),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val testProbe = TestProbe()

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO){
        override protected val ownRef = testProbe.ref
      }))

      toTest ! HostInfoUpdated(mockHostInfo, Some(mockHostInfoOld))

      testProbe.expectMsg(5.seconds, CheckParameterString("computerName",DiffPair("fake_computer_name_updated","fake_computer_name").get))
      testProbe.reply(ParameterInRange("computerName"))
      testProbe.expectMsg(5.seconds, CheckParameterStringList("ipAddresses",DiffPair(List("new_ip_address"),List()).get))
      testProbe.reply(ParameterInRange("ipAddresses"))
      testProbe.expectNoMessage(5.seconds)
    }

    "dispatch TriggerAlert for any field whose check has been replied with ParameterAlert" in new AkkaTestkitSpecs2Support {
      val mockHostInfo = HostInfo(
        "fake_host",
        "fake_computer_name_updated",
        "fake_model",
        "fake_uuid",
        List("new_ip_address"),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val mockHostInfoOld = HostInfo(
        "fake_host",
        "fake_computer_name",
        "fake_model",
        "fake_uuid",
        List(),
        None,
        None,
        None,
        ZonedDateTime.now()
      )

      val testProbe = TestProbe()

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, mockDAO){
        override protected val ownRef = testProbe.ref
      }))

      toTest ! HostInfoUpdated(mockHostInfo, Some(mockHostInfoOld))

      testProbe.expectMsg(5.seconds, CheckParameterString("computerName",DiffPair("fake_computer_name_updated","fake_computer_name").get))
      testProbe.reply(ParameterInRange("computerName"))
      testProbe.expectMsg(5.seconds, CheckParameterStringList("ipAddresses",DiffPair(List("new_ip_address"),List()).get))
      testProbe.reply(ParameterAlert("ipAddresses","test message"))
      testProbe.expectMsg(5.seconds, TriggerAlerts("fake_host", Seq(ParameterAlert("ipAddresses","test message"))))
    }
  }

  "AlertsActor!TriggerAlerts" should {
    "call alertHistoryDAO.addAlertNoDupe for each alert given" in new AkkaTestkitSpecs2Support {
      val testProbe = TestProbe()
      val testDAO = mock[AlertHistoryDAO]
      val mockedResponse = mock[RequestSuccess[UpdateResponse]]
      testDAO.addAlertNoDupe(any, any, any, any, any) returns Future(Right(mockedResponse))

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, testDAO){
        override protected val ownRef = testProbe.ref
      }))

      val testMsg = TriggerAlerts("fake_host", Seq(
        ParameterAlert("ipAddresses", "wrong ip address"),
        ParameterAlert("fibrechannel", "fibrecannel broke")
      ))

      val response = Await.result(toTest ? testMsg, 10.seconds)

      there was one(testDAO).addAlertNoDupe("fake_host","ipAddresses","wrong ip address", false, None)
      there was one(testDAO).addAlertNoDupe("fake_host","fibrechannel","fibrecannel broke", false, None)
      response mustEqual akka.actor.Status.Success
    }

    "not error on a 409 conflict" in new AkkaTestkitSpecs2Support {
      val testProbe = TestProbe()
      val testDAO = mock[AlertHistoryDAO]
      val mockedResponse =RequestFailure(409,None,Map(),null)
      testDAO.addAlertNoDupe(any, any, any, any, any) returns Future(Left(mockedResponse))

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, testDAO){
        override protected val ownRef = testProbe.ref
      }))

      val testMsg = TriggerAlerts("fake_host", Seq(
        ParameterAlert("ipAddresses", "wrong ip address"),
        ParameterAlert("fibrechannel", "fibrecannel broke")
      ))

      val response = Await.result(toTest ? testMsg, 10.seconds)

      there was one(testDAO).addAlertNoDupe("fake_host","ipAddresses","wrong ip address", false, None)
      there was one(testDAO).addAlertNoDupe("fake_host","fibrechannel","fibrecannel broke", false, None)
      response mustEqual akka.actor.Status.Success
    }

    "report back any non-409 error" in new AkkaTestkitSpecs2Support {
      val testProbe = TestProbe()
      val testDAO = mock[AlertHistoryDAO]
      val mockedResponse =RequestFailure(500,None,Map(),null)
      testDAO.addAlertNoDupe(any, any, any, any, any) returns Future(Left(mockedResponse))

      val toTest = system.actorOf(Props(new AlertsActor(system, blankConfig, testDAO)))

      val testMsg = TriggerAlerts("fake_host", Seq(
        ParameterAlert("ipAddresses", "wrong ip address"),
        ParameterAlert("fibrechannel", "fibrecannel broke")
      ))


      toTest.tell(testMsg, testProbe.ref)
      testProbe.expectMsgAllClassOf(classOf[akka.actor.Status.Failure])

      there was one(testDAO).addAlertNoDupe("fake_host","ipAddresses","wrong ip address", false, None)
      there was one(testDAO).addAlertNoDupe("fake_host","fibrechannel","fibrecannel broke", false, None)

    }
  }
}
