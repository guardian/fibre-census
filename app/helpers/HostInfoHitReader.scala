package helpers

import java.time.ZonedDateTime

import com.sksamuel.elastic4s.{Hit, HitReader}
import models.HostInfo

trait HostInfoHitReader {
//  implicit object ArchiveEntryHR extends HitReader[HostInfo] {
//    override def read(hit: Hit): Either[Throwable, HostInfo] = {
//      val size = try {
//        hit.sourceField("size").asInstanceOf[Long]
//      } catch {
//        case ex:java.lang.ClassCastException=>
//          hit.sourceField("size").asInstanceOf[Int].toLong
//      }
//
//      try {
//        val timestamp = ZonedDateTime.parse(hit.sourceField("last_modified").asInstanceOf[String])
//        Right(HostInfo(
//
//        ))
//      } catch {
//        case ex:Throwable=>
//          Left(ex)
//      }
//    }
//  }
}
