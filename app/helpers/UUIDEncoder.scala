package helpers

import java.util.UUID

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

trait UUIDEncoder {
  implicit val encodeUUID = new Encoder[UUID] {
    override def apply(a: UUID): Json = Json.fromString(a.toString)
  }

  implicit val decodeUUID = new Decoder[UUID] {
    override def apply(c: HCursor): Result[UUID] = for {
      c <- c.value.as[String]
    } yield UUID.fromString(c)
  }
}
