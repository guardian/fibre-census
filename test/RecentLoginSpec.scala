import java.time.temporal.TemporalAmount
import java.time.{Duration, LocalDateTime, ZonedDateTime}

import models.RecentLogin
import org.specs2.mutable._

import scala.io.Source
import scala.xml.parsing.ConstructingParser

class RecentLoginSpec extends Specification {
  "RecentLogin.fromXml" should {
    "process a parsed xml fragment" in {
      val sampleXml = "<recentLogins hostname=\"mycomputer\" location=\"console\" login=\"Sun Oct  7 11:41\" username=\"localhome\">\n    <duration days=\"0\" hours=\"23\" minutes=\"57\" />\n  </recentLogins>"
      val parser = ConstructingParser.fromSource(Source.fromString(sampleXml), preserveWS = false)
      val doc = parser.document().docElem
      val t = ZonedDateTime.now()
      val result = RecentLogin.fromXml(doc)

      result must beRight(RecentLogin(
        "mycomputer",
        "localhome",
        "console",
        LocalDateTime.of(2018,10,7,11,41),
        Duration.ofSeconds(86220)
      ))
    }
  }
}
