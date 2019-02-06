package models

import scala.annotation.switch
import scala.xml.NodeSeq

/*
          {
            'SignedBy' => 'Developer ID Application: ATTO Technology, Inc. (FC94733TZD), Developer ID Certification Authority, Apple Root CA',
            'Version' => '2.1.0',
            'DriverName' => 'ATTOExpressSASHBA2',
            'BundleID' => 'com.ATTO.driver.ATTOExpressSASHBA2',
            'Kind' => 'Intel',
            'KextVersion' => '2.1.0',
            'Location' => '/Library/Extensions/ATTOExpressSASHBA2.kext',
            'Dependencies' => 'Satisfied',
            'Loadable' => 'Yes',
            'Loaded' => 'No',
            'GetInfoString' => 'ATTO ExpressSAS HBA Driver 2.1.0 Copyright 2008-2013, ATTO Technology, Inc.'
          }
 */
object DriverInfo extends ((String, String, String, String, String, String,String, Boolean, Boolean, String)=>DriverInfo){
  def stringToBool(str:String):Boolean = (str.toLowerCase(): @switch) match {
    case "yes"=>true
    case "true"=>true
    case "1"=>true
    case "no"=>false
    case "false"=>false
    case "0"=>false
    case _=>false
  }

  def fromXml(node:NodeSeq):Either[String,DriverInfo] = {
    try{
      Right(new DriverInfo(
        node \@ "DriverName",
        node \@ "BundleID",
        node \@ "Version",
        node \@ "SignedBy",
        node \@ "KextVersion",
        node \@ "Location",
        node \@ "Dependencies",
        stringToBool(node \@ "Loadable"),
        stringToBool(node \@ "Loaded"),
        node \@ "GetInfoString"
      ))
    } catch {
      case ex:Throwable=>
        Left(ex.toString)
    }
  }

  /**
    * matches corresponding DriverInfo structs in `firstSeq` to the same one with corresponding name in `secondSeq`.
    * if no corresponding domain exists in `secondSeq`, then None is returned as the corresponding value
    * if no corresponding domain exists in `firstSeq`, then the value is not included in the return value
    * @param firstSeq first sequence of DriverInfo to check
    * @param secondSeq second sequence of DriverInfo to check
    * @return a Sequence of 2-tuples, same length as `firstSeq`. First element is the domain from firstSeq, second element is
    *         either the corresponding element from secondSeq or None.
    */
  def matchup(firstSeq:Seq[DriverInfo], secondSeq:Seq[DriverInfo]):Seq[Tuple2[DriverInfo,Option[DriverInfo]]] =
    firstSeq.map(di=>Tuple2(di, secondSeq.find(secondInf=>secondInf.driverName==di.driverName)))
}

case class DriverInfo(driverName:String, bundleId:String, version:String, signedBy:String, kextVersion:String,
                      location:String, dependencies:String, loadable:Boolean, loaded:Boolean, getInfoString:String)