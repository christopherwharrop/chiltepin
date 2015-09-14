case class RunCommandResult(status: Int, stdout: String, stderr: String)

trait RunCommand {

  import sys.process._

  def runCommand(cmd: String): RunCommandResult = {

    var status = 0    
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    
    try {
      status = cmd ! ProcessLogger(stdout append _ + "\n", stderr append _ + "\n")
      RunCommandResult(status,stdout.toString,stderr.toString)
    } catch {
      case ex: Throwable => RunCommandResult(1,stdout.toString,ex.getMessage)
    }


  }

}
