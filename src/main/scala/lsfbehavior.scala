///////////////////////////////////////////////////
//
// LSFBehavior class
//
///////////////////////////////////////////////////
class LSFBehavior extends BQBehavior {

  val stateMap = Map(
                   "EXIT" -> "Complete",
                   "DONE" -> "Complete",
                   "RUN" -> "Running",                 
                   "PEND" -> "Queued"
                 )

  ///////////////////////////////////////////////////
  //
  // submit
  //
  ///////////////////////////////////////////////////
  def submit(command: String, options: String, environment: Map[String,String]): BQSubmitResult = {

    // Pattern for extracting jobid from the output of qsub
    val jobRegEx = """(?s)(?m).*^Job <(\d+)> is submitted to .*queue.*$.*""".r

    // Run bsub
    val bsub = runCommand(s"bsub $options $command")

    // Look for jobid in output and extract it or report an error
    bsub.stdout match {
      case jobRegEx(jobId) => BQSubmitResult(Some(jobId), None)
      case _ => BQSubmitResult(None, Some(BQError(bsub.stderr, bsub.status)))
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
    val qstat = runCommand(s"bjobs -l -a $jobStr")

    // Try to parse the stdout and extract job status info
    // A status of 255 indicates at least one job has aged off, which is not an error
    try {
      qstat.status match {
        case 0 => BQStatusResult(Some(processQstat(qstat.stdout)), None)
        case 255 => if (qstat.stdout.isEmpty) BQStatusResult(Some(Map[String,BQJob]()), None) else BQStatusResult(Some(processQstat(qstat.stdout)), None)
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
  def processQstat(bjobsString: String): Map[String,BQJob] = {

    // Initialize an empty job queue
    val jobQueue = collection.mutable.Map[String, BQJob]()

    // Set up a pattern to unfuck the format of the bjobs output
    val joinLinesRegEx = """(?s)(?m)$\s\s\s\s\s\s\s\s\s\s\s\s\s\s\s+""".r

    // Set up a pattern to extract the job id and job state
    val jobStateRegEx = """^Job <(\d+)>,.*, Status <(\S+)>,.*$""".r

    // Get massaged output string
    val fixedString = joinLinesRegEx.replaceAllIn(bjobsString,"")

    // Extract job info from each line of bjobs output
    for (line <- fixedString.split("\\n")) {
      line match {
        case jobStateRegEx(jobId,nativeState) => jobQueue(jobId) = BQJob(jobId, stateMap.getOrElse(nativeState,"Unknown"), nativeState)
//        case _ => println(line)
      }
    }

    // Return the job queue
    jobQueue.toMap
    
  }

}