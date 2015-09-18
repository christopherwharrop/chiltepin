import scala.collection.mutable.Map
import akka.actor._
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.pipe

object Transition {
  case object GetReady
  case class Run(cmd: String)
  case class SubmitFailed(error: Option[BQError])
  case class SubmitSucceeded(jobid: String)
  case class StateUpdate(job: BQJob)
}

class Transition(h2DB: ActorRef, bqStat: ActorRef, bqSub: ActorRef, logger: ActorRef, placeNames: List[String]) extends Actor with Stash {

  import Transition._
  implicit val ec = context.dispatcher

  // Implicit timeout for getting place dependencies
  implicit val timeout = Timeout(1.second)


  val updateInterval =context.system.settings.config.getInt("chiltepin.update-interval")

  // A map of place names to place actor references
  val placeActors = collection.mutable.Map[String, ActorRef]()

  // Submission options
//  val options = "-A jetmgmt -l procs=1,partition=njet"
  val options = "-A nesccmgmt -l procs=1 -l walltime=00:05:00"

  var statusRequest: Cancellable = null

  // Helpers to get our place dependencies
  trait PlaceAcquisition
  case class PlaceAcquired(placeName: String, placeActor: ActorRef) extends PlaceAcquisition
  case class PlaceNotAcquired(t: Throwable) extends PlaceAcquisition

  // Send either PlacesAcquired or PlacesNotAcquired message to self for each place dependency
  placeNames foreach { acquirePlace(_) pipeTo self }

  def acquirePlace(placeName: String): Future[PlaceAcquisition] = {
    context.actorSelection("../" + placeName).resolveOne() map {
      placeActor => PlaceAcquired(placeName,placeActor)
    } recover {
      case t:Throwable => PlaceNotAcquired(t)
    }
  }


  def receive: Receive = waitingForPlaces

  def waitingForPlaces: Receive = {
    case PlaceAcquired(placeName, placeActor) =>

      logger ! Logger.Info(s"Collected reference for place $placeName",3)

      // Collect the place actor reference
      placeActors(placeName) = placeActor

      if (placeActors.size == placeNames.size) {

        logger ! Logger.Info(s"Collected references for all places",3)

        // Get all the messages we stashed and receive them
        unstashAll()

        // pass all our acquired dependencies in
        context.become(initialized)

      }

    case PlaceNotAcquired(t) => throw new IllegalStateException(s"Failed to acquire place: $t")

    // Any other message save for later
    case _ => stash()
  }

  // All our places have been acquired
  def initialized : Receive = {

    case Run(cmd) => 
      logger ! Logger.Info("Asking bqsub to submit the job",2)
      bqSub ! BqSub.Submit(cmd, options)
    case SubmitFailed(bqError) => 
      bqError match {
        case Some(error) => logger ! Logger.Info(s"ERROR: Could not submit job.  ${error.message}",2)
        case None => logger ! Logger.Info(s"ERROR: Could not submit job.  Reason unknown",2)
      }
    case SubmitSucceeded(jobid) => 
      h2DB ! H2DB.AddJob(jobid,"Submitted")
      logger ! Logger.Info(s"Submitted job $jobid",2)
      logger ! Logger.Info("Subscribing to job events",2)
      bqStat ! BqStat.WatchJob(self,jobid)
      statusRequest = context.system.scheduler.schedule(updateInterval.seconds,
                                                  updateInterval.seconds,
                                                  bqStat,
                                                  BqStat.StatusRequest(jobid))

    case StateUpdate(job) => 
      h2DB ! H2DB.UpdateJob(job.jobId,job.state)      
      logger ! Logger.Info(s"job ${job.jobId} is in state ${job.state}(${job.nativeState})",2)
      if (job.state == "Complete") {
        statusRequest.cancel()
        logger ! Logger.Info(s"Unsubscribing from job ${job.jobId}",2)
        bqStat ! BqStat.UnwatchJob(self,job.jobId)
        context.system.shutdown()
      }

  }

}
