import Workflow._

object Task {

  def create(name: String): Unit = { Workflow.tasks(name) = new Task(name) }

}


class Task(name: String) {

  var cmd = ""
  var opt = ""
  var env = Map[String,String]()

  println(s"Creating task $name")

  def runs(command: String) = { cmd = command }
  def options(options: String) = { opt = options }
  def environment(environment: Map[String,String]) = { env = environment }

}