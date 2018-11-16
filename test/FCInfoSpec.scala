import java.time.ZonedDateTime

import org.specs2.mutable._
import models.{FCDomain, FCInfo, HostInfo}

import scala.io.Source
import scala.xml.parsing.ConstructingParser


class FCInfoSpec extends Specification {
  "FCDomain.fromXml" should {
    "parse a domain line fragment" in {
      val sampleXml = """<?xml version="1.0"?>
                        |<data computerName="32811_TV06_B300_K3_MMEDIA_1" hostname="32811.gnm.int">
                        |  <fibrechannel Product="">
                        |    <Domain_0 WWN="10:00:00:10:86:04:09:AA" lunCount="0"/>
                        |    <Domain_1 Speed="Automatic (8 Gigabit)" Status="Link Established" WWN="24:70:00:C0:FF:1B:00:2C" lunCount="120"/>
                        |  </fibrechannel>
                        |  <ipAddresses>10.232.244.28</ipAddresses>
                        |  <ipAddresses>192.168.51.23</ipAddresses>
                        |</data>"""
      val parser = ConstructingParser.fromSource(Source.fromString(sampleXml), preserveWS = false)
      val doc = parser.document().docElem

      val result = FCDomain.fromXml(doc \ "fibrechannel" \ "Domain_0")
      result must beRight(FCDomain("Domain_0",None,None,Some("10:00:00:10:86:04:09:AA"),0))
    }
  }

  "FCInfo.fromXml" should {
    "parse a fibrechannel fragment" in {
      val sampleXml = """<?xml version="1.0"?>
                        |<data computerName="32811_TV06_B300_K3_MMEDIA_1" hostname="32811.gnm.int">
                        |  <fibrechannel Product="ATTO Thunderlink">
                        |    <Domain_0 WWN="10:00:00:10:86:04:09:AA" lunCount="0"/>
                        |    <Domain_1 Speed="Automatic (8 Gigabit)" Status="Link Established" WWN="24:70:00:C0:FF:1B:00:2C" lunCount="120"/>
                        |  </fibrechannel>
                        |  <ipAddresses>10.232.244.28</ipAddresses>
                        |  <ipAddresses>192.168.51.23</ipAddresses>
                        |</data>"""
      val parser = ConstructingParser.fromSource(Source.fromString(sampleXml), preserveWS = false)
      val doc = parser.document().docElem

      val result = FCInfo.fromXml(doc \ "fibrechannel")
      result must beRight(
        FCInfo(
          Seq(
            FCDomain("Domain_0",None,None,Some("10:00:00:10:86:04:09:AA"),0),
            FCDomain("Domain_1",Some("Automatic (8 Gigabit)"),Some("Link Established"),Some("24:70:00:C0:FF:1B:00:2C"),120)
          ),"ATTO Thunderlink"))
    }

    "give None if fibrechannel fragment is empty" in {
      val sampleXml = """<?xml version="1.0"?>
                        |<data computerName="32811_TV06_B300_K3_MMEDIA_1" hostname="32811.gnm.int">
                        |  <fibrechannel Product="ATTO Thunderlink">
                        |    <Domain_0 WWN="10:00:00:10:86:04:09:AA" lunCount="0"/>
                        |    <Domain_1 Speed="Automatic (8 Gigabit)" Status="Link Established" WWN="24:70:00:C0:FF:1B:00:2C" lunCount="120"/>
                        |  </fibrechannel>
                        |  <ipAddresses>10.232.244.28</ipAddresses>
                        |  <ipAddresses>192.168.51.23</ipAddresses>
                        |</data>"""
      val parser = ConstructingParser.fromSource(Source.fromString(sampleXml), preserveWS = false)
      val doc = parser.document().docElem

      val result = FCInfo.fromXml(doc \ "fibrechannel")
      result must beRight(
        FCInfo(
          Seq(
            FCDomain("Domain_0",None,None,Some("10:00:00:10:86:04:09:AA"),0),
            FCDomain("Domain_1",Some("Automatic (8 Gigabit)"),Some("Link Established"),Some("24:70:00:C0:FF:1B:00:2C"),120)
          ),"ATTO Thunderlink"))
    }
  }

}
