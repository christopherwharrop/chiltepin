///////////////////////////////////////////////////
//
// TorqueBehavior class
//
///////////////////////////////////////////////////
class TorqueBehavior extends BQBehavior {

  val stateMap = Map(
                   "C" -> "Complete",
                   "E" -> "Running",
                   "H" -> "Hold",
                   "Q" -> "Queued",
                   "R" -> "Running",
                   "T" -> "Unknown",
                   "W" -> "Queued"
                 )

  ///////////////////////////////////////////////////
  //
  // submit
  //
  ///////////////////////////////////////////////////
  def submit(command: String, options: String): BQSubmitResult = {

    // Pattern for extracting jobid from the output of qsub
    val jobRegEx = """(?s)(?m).*^((\d+)(?:\.[a-zA-Z0-9-]+)*)$.*""".r

    // Run qsub
    val qsub = runCommand(s"qsub $options $command")

    // Look for jobid in output and extract it or report an error
    qsub.stdout match {
      case jobRegEx(fqJobId,jobId) => BQSubmitResult(Some(jobId), None)
      case _ => BQSubmitResult(None, Some(BQError(qsub.stderr, qsub.status)))
    }

  }


  ///////////////////////////////////////////////////
  //
  // status
  //
  ///////////////////////////////////////////////////
  def status(jobs: List[String]): BQStatusResult = {

    // Construct job list string   
    val jobStr = jobs.mkString(" ")

    // Run qstat
    val qstat = runCommand(s"qstat -x $jobStr")

    // Try to parse the stdout and extract job status info
    // A status of 153 indicates at least one job has aged off, which is not an error
    try {
      qstat.status match {
        case 0 => BQStatusResult(Some(processQstat(qstat.stdout)), None)
        case 153 => if (qstat.stdout.isEmpty) BQStatusResult(Some(Map[String,BQJob]()), None) else BQStatusResult(Some(processQstat(qstat.stdout)), None)
        case _ => BQStatusResult(None, Some(BQError(qstat.stderr, qstat.status)))
      }
    } catch {
      // Return an error if stdout is not well-formed XML	
      case e: org.xml.sax.SAXParseException => BQStatusResult(None,Some(BQError(qstat.stderr, qstat.status)))
    }

  }


  ///////////////////////////////////////////////////
  //
  // processQstat
  //
  ///////////////////////////////////////////////////
  def processQstat(xmlString: String): Map[String,BQJob] = {

    // Initialize an empty job queue
    val jobQueue = collection.mutable.Map[String, BQJob]()

    // Extract job info from each line of qstat output
    for (jobstr <- xmlString.lines) {
      val root = xml.XML.loadString(jobstr)
      root match {
        case <Data>{jobs @ _*}</Data> =>
          for (job <- jobs) {
            val jobId =  (job \ "Job_Id").text.split('.')(0)
            val nativeState = (job \ "job_state").text
            val state = stateMap.getOrElse(nativeState,"Unknown")
            jobQueue(jobId) = BQJob(jobId, state, nativeState)
          }
      }
    }

    // Return the job queue
    jobQueue.toMap
    
  }

}