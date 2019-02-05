import akka.actor.Props
import models.{DiffPair, FCDomain, FCInfo}
import org.specs2.mutable.Specification
import io.circe.generic.auto._
import akka.pattern.ask
import services.AlertsActor

import scala.concurrent.Await
import scala.concurrent.duration._

class AlertsActorSpec extends Specification  {
  sequential
  implicit val timeout:akka.util.Timeout = 10.seconds

  import services.AlertsActor._

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
      val toTest = system.actorOf(Props(new AlertsActor(system)))

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
      val toTest = system.actorOf(Props(new AlertsActor(system)))

      val result = Await.result(toTest ? CheckParameterFcInfo("test-field",d.get), 10.seconds)
      result.asInstanceOf[AnyRef] must beAnInstanceOf[ParameterAlert]
    }
  }
}
