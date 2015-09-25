import akka.actor._
import scala.concurrent.Future
import akka.pattern.pipe

///////////////////////////////////////////////////
//
// BqSub Companion Object
//
///////////////////////////////////////////////////
object BqSub {

  // Provide a Props for actor creation
  def props(bqBehavior: BQBehavior, logger: ActorRef): Props = Props(new BqSub(bqBehavior,logger))

  // BqSub messages
  case class Submit(script: String, options: String)
  case class SubmitResult(requestor: ActorRef, result: BQSubmitResult)

}

///////////////////////////////////////////////////
//
// BqSub Actor
//
///////////////////////////////////////////////////
class BqSub(bqBehavior: BQBehavior, logger: ActorRef) extends Actor with RunCommand {

  import BqSub._

  implicit val ec = context.dispatcher

  ///////////////////////////////////////////////////
  //
  // receive
  //
  ///////////////////////////////////////////////////
  def receive = {
    case Submit(script,options) =>
      val requestor = sender
      val qsub = Future(bqBehavior.submit(script,options)) map { result => SubmitResult(requestor, result) } pipeTo self
    case SubmitResult(requestor, result) =>
      result.jobId match {
        case Some(jobId) => requestor ! Transition.SubmitSucceeded(jobId)
        case None => requestor ! Transition.SubmitFailed(result.error)
      }
  }

}

