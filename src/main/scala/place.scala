import scala.collection.mutable.Map
import akka.actor._
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.pipe


object Place {
  case object GetReady
}

class Place(transitionNames: List[String])(implicit logger: LoggerWrapper) extends Actor with Stash {

  import Place._
  implicit val ec = context.dispatcher

  // Implicit timeout for getting transition dependencies
  implicit val timeout = Timeout(1.second)

  // A map of transition names to transition actor references
  val transitionActors = collection.mutable.Map[String, ActorRef]()

  // Helpers to get our transition dependencies
  trait TransitionAcquisition
  case class TransitionAcquired(transitionName: String, transitionActor: ActorRef) extends TransitionAcquisition
  case class TransitionNotAcquired(t: Throwable) extends TransitionAcquisition

  // Send either TransitionsAcquired or TransitionsNotAcquired message to self for each transition dependency
  transitionNames foreach { acquireTransition(_) pipeTo self }

  def acquireTransition(transitionName: String): Future[TransitionAcquisition] = {
    context.actorSelection("../" + transitionName).resolveOne() map {
      transitionActor => TransitionAcquired(transitionName,transitionActor)
    } recover {
      case t:Throwable => TransitionNotAcquired(t)
    }
  }

  def receive: Receive = waitingForTransitions

  def waitingForTransitions: Receive = {
    case TransitionAcquired(transitionName, transitionActor) =>

      logger.actor ! Logger.Info(s"Collected reference for transition $transitionName",3)

      // Collect the transition actor reference
      transitionActors(transitionName) = transitionActor

      if (transitionActors.size == transitionNames.size) {

        logger.actor ! Logger.Info(s"Collected references for all transitions",3)

        // Get all the messages we stashed and receive them
        unstashAll()

        // pass all our acquired dependencies in
        context.become(initialized)

      }

    case TransitionNotAcquired(t) => throw new IllegalStateException(s"Failed to acquire transition: $t")

    // Any other message save for later
    case _ => stash()
  }

  // All our transitions have been acquired
  def initialized : Receive = {

    case GetReady => println("ready")

  }

}
