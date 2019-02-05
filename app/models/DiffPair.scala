package models

import io.circe.{Decoder, Encoder}

case class DiffPair[+T:Encoder :Decoder](newValue:T, oldValue:T)

object DiffPair {
  def apply[T:Encoder :Decoder](firstVal:T,secondVal:T):Option[DiffPair[T]] = {
    if(firstVal==secondVal){
      None
    } else {
      Some(new DiffPair[T](firstVal, secondVal))
    }
  }
}