import akka.actor._
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.pipe
import scala.collection.mutable.ListBuffer

object Transition {
  case object GetReady
  case class SetCommand(cmd: String)
  case class SetOptions(opt: String)
  case class SetEnvironment(env: Map[String,String])
  case class AddInputDependency(name: String)
  case object Go
  case object Run
  case class SubmitFailed(error: Option[BQError])
  case class SubmitSucceeded(jobid: String)
  case class StateUpdate(job: BQJob)
}

class Transition()(implicit logger: LoggerWrapper, h2DB: H2DBWrapper, bqGateway: BQGatewayWrapper) extends Actor with Stash {

  import Transition._
  implicit val ec = context.dispatcher

  // Implicit timeout for getting place dependencies
  implicit val timeout = Timeout(1.second)


  val updateInterval = context.system.settings.config.getInt("update-interval")

  // A map of place names to place actor references
  val inputPlaceNames = new ListBuffer[String]
  val outputPlaceNames = new ListBuffer[String]
  val inputPlaceActors = collection.mutable.Map[String, ActorRef]()
  val outputPlaceActors = collection.mutable.Map[String, ActorRef]()

  // Initialize transition options
  var command = ""
  var options = ""
  var environment = Map[String,String]()

  var statusRequest: Cancellable = null

  // Helpers to get our place dependencies
  trait PlaceAcquisition
  case class PlaceAcquired(placeName: String, placeActor: ActorRef) extends PlaceAcquisition
  case class PlaceNotAcquired(t: Throwable) extends PlaceAcquisition

  def acquirePlace(placeName: String): Future[PlaceAcquisition] = {
    context.actorSelection(s"/user/workflow/$placeName/status").resolveOne() map {
      placeActor => PlaceAcquired(placeName,placeActor)
    } recover {
      case t:Throwable => PlaceNotAcquired(t)
    }
  }


  override def preStart() {

    // Create the status output place
    outputPlaceNames += "status"
    outputPlaceActors("status") =  context.actorOf(Props(new Place), name = "status")
    
  }

  def receive: Receive = configuring

  def configuring: Receive = {
    case SetCommand(cmd: String) => command = cmd
    case SetOptions(opt :String) => options = opt
    case SetEnvironment(env : Map[String,String]) => environment = env
    case AddInputDependency(name : String) => inputPlaceNames += name
    case Go => 
      if (inputPlaceNames.size == 0) {

        // Get all the messages we stashed and receive them
        unstashAll()

        // Run
        self ! Run

        // pass all our acquired dependencies in
        context.become(initialized)

      } else {
        // Tell our input places who we are
        inputPlaceNames foreach { acquirePlace(_) pipeTo self }
      }

    case PlaceAcquired(placeName, placeActor) =>

      logger.actor ! Logger.Info(s"Collected reference for place $placeName/status",3)

      // Register this transition with the input place
      placeActor ! Place.AddTransition(self)

      // Collect the place actor reference
      inputPlaceActors(placeName) = placeActor

      if (inputPlaceActors.size == inputPlaceNames.size) {
        logger.actor ! Logger.Info(s"Collected references for all places",3)

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

    case Run => 
      logger.actor ! Logger.Info("Asking bqsub to submit the job",2)
      bqGateway.actor ! BqGateway.Submit(command, options, environment)
    case SubmitFailed(bqError) => 
      bqError match {
        case Some(error) => logger.actor ! Logger.Info(s"ERROR: Could not submit job.  ${error.message}",2)
        case None => logger.actor ! Logger.Info(s"ERROR: Could not submit job.  Reason unknown",2)
      }
    case SubmitSucceeded(jobid) => 
      h2DB.actor ! H2DB.AddJob(jobid,"Submitted")
      logger.actor ! Logger.Info(s"Submitted job $jobid",2)
      logger.actor ! Logger.Info("Subscribing to job events",2)
      bqGateway.actor ! BqGateway.WatchJob(self,jobid)
      statusRequest = context.system.scheduler.schedule(updateInterval.seconds,
                                                  updateInterval.seconds,
                                                  bqGateway.actor,
                                                  BqGateway.StatusRequest(jobid))
    case StateUpdate(job) => 
      h2DB.actor ! H2DB.UpdateJob(job.jobId,job.state)      
      logger.actor ! Logger.Info(s"job ${job.jobId} is in state ${job.state}(${job.nativeState})",2)
      if (job.state == "Complete") {
        outputPlaceActors("status") ! Place.Token
        statusRequest.cancel()
        logger.actor ! Logger.Info(s"Unsubscribing from job ${job.jobId}",2)
        bqGateway.actor ! BqGateway.UnwatchJob(self,job.jobId)
//        context.system.shutdown()
      }

  }

}
