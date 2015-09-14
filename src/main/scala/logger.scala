import akka.actor._
import org.joda.time._
import org.joda.time.format._

object Logger {

  case object GetReady
  case class  Info(msg: String, level: Int)
  case class  Warn(msg: String)
  case class  Error(msg: String)
  case object Stop
}



class Logger extends Actor {

  import Logger._
  import org.joda.time._

  implicit val ec = context.dispatcher

  def getTimeStr(): String = {
    val localTime = new DateTime
    val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss zzz")
    dateFormat.print(localTime.withZone(DateTimeZone.forOffsetHours(0)))
  }

  def receive = {
    case Info(msg,level) => println(s"$getTimeStr [INFO $level] $msg")
    case Warn(msg) => println(s"$getTimeStr [WARNING] $msg")
    case Error(msg) => println(s"$getTimeStr [ERROR] $msg")
  }

}