///////////////////////////////////////////////////
//
// MoabTorqueBehavior class
//
///////////////////////////////////////////////////
class MoabTorqueBehavior extends BQBehavior {

  val torqueBehavior = new TorqueBehavior
  val moabBehavior = new MoabBehavior

  ///////////////////////////////////////////////////
  //
  // submit
  //
  ///////////////////////////////////////////////////
  def submit(command: String, options: String, environment: Map[String,String]): BQSubmitResult = {

    // Submit the job with Torque
    torqueBehavior.submit(command, options, environment)

  }


  ///////////////////////////////////////////////////
  //
  // status
  //
  ///////////////////////////////////////////////////
  def status(jobs: List[String]): BQStatusResult = {

    // Ask Torque for the job status
    val torqueResult = torqueBehavior.status(jobs)
    
    // Get a list of jobs that Torque didn't find
    val missingJobs = torqueResult match {
      case BQStatusResult(Some(torqueJobs), None) => jobs.filterNot(torqueJobs.keys.toList contains _)
      case BQStatusResult(None, Some(error)) => jobs
    }

    if (missingJobs.isEmpty) {
      // If Torque found all the jobs, return the result
      torqueResult
    } else {
      // Otherwise, ask Moab for the status of the missing jobs
      val moabResult = moabBehavior.status(missingJobs)
      moabResult match {
        case BQStatusResult(Some(moabJobs), None) => BQStatusResult(Some(torqueResult.jobMap.getOrElse(Map[String,BQJob]()) ++ moabJobs), torqueResult.error)
        case BQStatusResult(None, Some(error)) => BQStatusResult(torqueResult.jobMap, Some(error))
      }
    }

  }

}