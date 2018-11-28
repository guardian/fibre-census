import java.time.{Duration, LocalDateTime, ZonedDateTime}

import org.specs2.mutable._
import models._

import scala.io.Source
import scala.xml.parsing.ConstructingParser


class HostInfoSpec extends Specification {
  "HostInfo.fromXML" should {
    "convert an xml doc with no fibrechannel info into a HostInfo model" in {
      val sampleXml = """<?xml version="1.0" encoding="UTF-8"?>
                        |<data model="something" hw_uuid="fakeid" computerName="33212_TV01_MMED_TEC_LOCADM_" hostname="33212.gnm.int">
                        |  <fibrechannel></fibrechannel>
                        |    <ipAddresses>192.168.1.108</ipAddresses>
                        |    <ipAddresses>192.168.1.12</ipAddresses>
                        |    <ipAddresses>192.168.1.19</ipAddresses>
                        |</data>""".stripMargin
      val parser = ConstructingParser.fromSource(Source.fromString(sampleXml), preserveWS = false)
      val doc = parser.document().docElem
      val t = ZonedDateTime.now()
      val result = HostInfo.fromXml(doc, t)
      result must beRight(HostInfo("33212.gnm.int","33212_TV01_MMED_TEC_LOCADM_","something","fakeid",List("192.168.1.108","192.168.1.12","192.168.1.19"),None,None,None,t))
    }

    "convert an xml doc with fibrechannel info into a HostInfo model" in {
      val sampleXml = """<?xml version="1.0"?>
                        |<data model="something" hw_uuid="fakeid" computerName="32811_TV06_B300_K3_MMEDIA_1" hostname="32811.gnm.int">
                        |  <fibrechannel Product="ATTO ThunderLink FC 2082">
                        |    <Domain_0 WWN="10:00:00:10:86:04:09:AA" lunCount="0"/>
                        |    <Domain_1 Speed="Automatic (8 Gigabit)" Status="Link Established" WWN="24:70:00:C0:FF:1B:00:2C" lunCount="120"/>
                        |  </fibrechannel>
                        |  <ipAddresses>10.232.244.28</ipAddresses>
                        |  <ipAddresses>192.168.51.23</ipAddresses>
                        |</data>""".stripMargin
      val parser = ConstructingParser.fromSource(Source.fromString(sampleXml), preserveWS = false)
      val doc = parser.document().docElem
      val t = ZonedDateTime.now()
      val result = HostInfo.fromXml(doc, t)
      result must beRight(HostInfo("32811.gnm.int","32811_TV06_B300_K3_MMEDIA_1","something","fakeid",
        List("10.232.244.28","192.168.51.23"),
        Some(FCInfo(
          Seq(
            FCDomain("Domain_0",None,None,Some("10:00:00:10:86:04:09:AA"),0),
            FCDomain("Domain_1",Some("Automatic (8 Gigabit)"), Some("Link Established"), Some("24:70:00:C0:FF:1B:00:2C"), 120)
          ),"ATTO ThunderLink FC 2082"
        )),
        None,
        None,
        t))
    }

    "convert an xml doc with driver info into a HostInfo model" in {
      val sampleXml = """<?xml version="1.0"?>
                        |<data model="something" hw_uuid="fakeid" computerName="32811_TV06_B300_K3_MMEDIA_1" hostname="32811.gnm.int">
                        |  <fibrechannel Product="ATTO ThunderLink FC 2082">
                        |    <Domain_0 WWN="10:00:00:10:86:04:09:AA" lunCount="0"/>
                        |    <Domain_1 Speed="Automatic (8 Gigabit)" Status="Link Established" WWN="24:70:00:C0:FF:1B:00:2C" lunCount="120"/>
                        |  </fibrechannel>
                        |  <driverInfo>
                        |    <driver BundleID="com.hardware.driver.something" Dependencies="Satisfied"
                        |    DriverName="something"
                        |    GetInfoString="Some kind of driver"
                        |     KextVersion="2.1.3"
                        |     Kind="Intel" Loadable="Yes" Loaded="No"
                        |     Location="/Library/Extensions/somethingdriver.kext"
                        |     SignedBy="Developer ID Application: MyCorp (FC94733TZD), Developer ID Certification Authority, Apple Root CA"
                        |     Version="2.1.3" />
                        |  </driverInfo>
                        |  <ipAddresses>10.232.244.28</ipAddresses>
                        |  <ipAddresses>192.168.51.23</ipAddresses>
                        |</data>""".stripMargin
      val parser = ConstructingParser.fromSource(Source.fromString(sampleXml), preserveWS = false)
      val doc = parser.document().docElem
      val t = ZonedDateTime.now()
      val result = HostInfo.fromXml(doc, t)
      result must beRight(HostInfo("32811.gnm.int","32811_TV06_B300_K3_MMEDIA_1","something","fakeid",
        List("10.232.244.28","192.168.51.23"),
        Some(FCInfo(
          Seq(
            FCDomain("Domain_0",None,None,Some("10:00:00:10:86:04:09:AA"),0),
            FCDomain("Domain_1",Some("Automatic (8 Gigabit)"), Some("Link Established"), Some("24:70:00:C0:FF:1B:00:2C"), 120)
          ),"ATTO ThunderLink FC 2082"
        )),
        None,
        Some(Seq(
          DriverInfo("something","com.hardware.driver.something","2.1.3","Developer ID Application: MyCorp (FC94733TZD), Developer ID Certification Authority, Apple Root CA","2.1.3","/Library/Extensions/somethingdriver.kext", "Satisfied",true,false,"Some kind of driver")
        )),
        t))
    }
  }
}
