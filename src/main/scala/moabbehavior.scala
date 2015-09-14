///////////////////////////////////////////////////
//
// MoabBehavior class
//
///////////////////////////////////////////////////
class MoabBehavior extends BQBehavior {

  val stateMap = Map(
                   "Completed" -> "Complete",
                   "Running" -> "Running",
                   "UserHold" -> "Hold",
                   "SystemHold" -> "Hold",
                   "BatchHold" -> "Hold",
                   "Deferred" -> "Hold",
                   "Idle" -> "Queued"
                 )

  ///////////////////////////////////////////////////
  //
  // submit
  //
  ///////////////////////////////////////////////////
  def submit(command: String, options: String): BQSubmitResult = {

    // Pattern for extracting jobid from the output of msub
    val jobRegEx = """(?s)(?m).*^((?:[a-zA-Z0-9-]+\.)*(\d+))$.*""".r

    // Run msub
    val msub = runCommand(s"msub $options $command")

    // Look for jobid in output and extract it or return an error
    msub.stdout match {
      case jobRegEx(fqJobId,jobId) => BQSubmitResult(Some(jobId), None)
      case _ => BQSubmitResult(None, Some(BQError(msub.stderr, msub.status)))
    }

  }


  ///////////////////////////////////////////////////
  //
  // status
  //
  ///////////////////////////////////////////////////
  def status(jobs: List[String]): BQStatusResult = {


    // Run standard showq
    var showq = runCommand(s"showq --noblock --xml -u ${sys.env("USER")}")

    // Try to parse the showq output and extract job status info    
    try {

      // Get list of jobs that are blocked, queued, or running
      val showqJobs = processShowq(showq.stdout,jobs)

      // Check to see if there are jobs unaccounted for
      val missingJobs = jobs.filterNot(showqJobs.keys.toList contains _)

      // Check to see if the missing jobs have completed
      if (missingJobs.isEmpty) {

        // If there are no missing jobs, return the ones we've found
        BQStatusResult(Some(showqJobs), None)

      } else {

        // Run showq for completed jobs
        showq = runCommand(s"showq -c --noblock --xml -u ${sys.env("USER")}")

        // Get list of jobs that have completed
        val showqCJobs = processShowq(showq.stdout,missingJobs)

        // Return the complete list of jobs
        BQStatusResult(Some(showqJobs ++ showqCJobs), None)

      }

    } catch {

      // Return an error if stdout from showq is not well-formed XML
      case e: org.xml.sax.SAXParseException => BQStatusResult(None, Some(BQError(showq.stderr, showq.status)))

    }

  }


  ///////////////////////////////////////////////////
  //
  // processShowq
  //
  ///////////////////////////////////////////////////
  def processShowq(xmlString: String, jobs: List[String]): Map[String,BQJob] = {

//<job AWDuration="18406" Account="jetmgmt" Class="urgent" DRMJID="49134660.jetbqs3" EEDuration="11" GJID="49134660" Group="jetmgmt" JobID="49134660" JobName="STDIN" MasterHost="s163" PAL="sjet" QOS="urgent" ReqAWDuration="28800" ReqProcs="16" RsvStartTime="1438010623" RunPriority="50000" StartPriority="50000" StartTime="1438010623" StatPSDed="294495.200000" StatPSUtl="224784.171400" State="Running" SubmissionTime="1438010612" SuspendDuration="0" User="Christopher.W.Harrop"></job>

//<job AWDuration="437" Account="jetmgmt" Class="batch" CompletionCode="0" CompletionTime="1438033081" DRMJID="49188739.jetbqs3" EEDuration="6" GJID="49188739" Group="jetmgmt" JobID="49188739" JobName="test.sh" MasterHost="n374" PAL="njet" QOS="batch" ReqAWDuration="1800" ReqNodes="1" ReqProcs="1" StartTime="1438032644" StatPSDed="368.010000" StatPSUtl="368.010000" State="Completed" SubmissionTime="1438032638" SuspendDuration="0" User="Christopher.W.Harrop"></job>

    // Initialize an empty job queue
    val jobQueue = collection.mutable.Map[String, BQJob]()

    // Extract job info from showq output for each job in jobs list
    val root = xml.XML.loadString(xmlString)
    for (job <- (root \\ "job").filter { node => jobs contains (node \ "@JobID").text } ) {
      val jobId =  (job \ "@JobID").toString
      val nativeState = (job \ "@State").toString
      val state = stateMap.getOrElse(nativeState,"Unknown")
      jobQueue(jobId) = BQJob(jobId, state, nativeState)
    }

    // Return the job queue
    jobQueue.toMap
    
  }

}