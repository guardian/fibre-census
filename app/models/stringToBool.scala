package models

trait stringToBool {
  def stringToBool(str:String):Boolean = str.toLowerCase() match {
    case "yes"=>true
    case "true"=>true
    case "1"=>true
    case "no"=>false
    case "false"=>false
    case "0"=>false
    case _=>false
  }
}
