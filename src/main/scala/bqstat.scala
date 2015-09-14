import scala.collection.mutable.Map
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.pattern.pipe

///////////////////////////////////////////////////
//
// BqStat Companion Object
//
///////////////////////////////////////////////////
object BqStat {

  // Provide a Props for actor creation
  def props(bqBehavior: BQBehavior, logger: ActorRef): Props = Props(new BqStat(bqBehavior, logger))

  // BqStat messages
  case object GetUpdate
  case class  Update(queue: BQStatusResult) 
  case class  WatchJob(requestor: ActorRef,jobId: String)
  case class  UnwatchJob(requestor: ActorRef,jobId: String)
  case class  StatusRequest(jobId: String)
  case object Showq

}

///////////////////////////////////////////////////
//
// BqStat Actor
//
///////////////////////////////////////////////////
class BqStat(bqBehavior: BQBehavior, logger: ActorRef) extends Actor with RunCommand {

  import BqStat._

  implicit val ec = context.dispatcher

  val updateInterval = context.system.settings.config.getInt("bqserver.bqstat.update-interval")
  var statusMap = collection.mutable.Map[String, BQJob]()
  var watchingMap = collection.mutable.Map[ActorRef, List[String]]()
  var schedUp = true

  ///////////////////////////////////////////////////
  //
  // preStart
  //
  ///////////////////////////////////////////////////
  override def preStart() {

    self ! GetUpdate

  }


  ///////////////////////////////////////////////////
  //
  // receive
  //
  ///////////////////////////////////////////////////
  def receive = {

    // Ask the batch system for a job status update
    case GetUpdate =>
      val jobList = watchingMap.values.flatten.toList.filterNot(statusMap.filter(_._2.state == "Complete") contains _)
      if (jobList.isEmpty) context.system.scheduler.scheduleOnce(updateInterval.seconds) { self ! GetUpdate }
      else {
        val qstat = Future { bqBehavior.status(jobList) } map { result => Update(result) } pipeTo self
      }

    // Update our state with new status from the batch system
    case Update(result) => 

      // Schedule the next update request
      context.system.scheduler.scheduleOnce(updateInterval.seconds) { self ! GetUpdate }

      // Merge new status results into status of completed jobs
      result match {
        case BQStatusResult(Some(updateMap), None) => 
          schedUp = true
          statusMap = statusMap.filter(_._2.state == "Complete") ++ updateMap
        case BQStatusResult(Some(updateMap), Some(error)) =>
          schedUp = false
          statusMap = statusMap.filter(_._2.state == "Complete") ++ updateMap
          logger ! Logger.Info(s"WARNING: BQStat error.  ${error.message}",2)     
        case BQStatusResult(None, Some(error)) =>
          schedUp = false
          statusMap = statusMap.filter(_._2.state == "Complete")
          logger ! Logger.Info(s"WARNING: BQStat error.  ${error.message}",2)     
      }

    // Job status request
    case StatusRequest(jobId) =>
      if (schedUp) sender ! Transition.StateUpdate(statusMap.getOrElse(jobId, BQJob(jobId,"Unknown","")))
      else sender ! Transition.StateUpdate(statusMap.getOrElse(jobId, BQJob(jobId,"Unavailable","")))
      
    // Watch a job
    case WatchJob(subscriber,jobid) =>
      if (watchingMap.contains(subscriber)) watchingMap(subscriber) = jobid :: watchingMap(subscriber)
      else watchingMap(subscriber) = List(jobid)

    // Unwatch about a job
    case UnwatchJob(subscriber,jobid) =>
      watchingMap(subscriber) = watchingMap(subscriber).filter(_ != jobid)
      if (watchingMap(subscriber).isEmpty) watchingMap = watchingMap - subscriber
      if (! watchingMap.values.flatten.toList.contains(jobid)) { statusMap = statusMap - jobid }

    case Showq => 
      for ((k,v) <- statusMap) {
        logger ! Logger.Info(s"$k is in state $v",2)
      }

  }

}
