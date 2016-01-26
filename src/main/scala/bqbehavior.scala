case class BQJob(jobId: String, state: String, nativeState: String)
case class BQError(message: String, status: Int)
case class BQSubmitResult(jobId: Option[String], error: Option[BQError])
case class BQStatusResult(jobMap: Option[Map[String,BQJob]], error: Option[BQError])

///////////////////////////////////////////////////
//
// Abstract BQBehavior class
//
///////////////////////////////////////////////////
abstract class BQBehavior extends RunCommand {

  def submit(command: String, options: String, environment: Map[String,String]): BQSubmitResult
  def status(jobIds: List[String]): BQStatusResult

}

