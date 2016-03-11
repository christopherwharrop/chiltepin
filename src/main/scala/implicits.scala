import scala.language.implicitConversions
import Workflow._

object ChiltepinImplicits {

  implicit def String2Task(s: String):Task = Workflow.tasks(s)

}