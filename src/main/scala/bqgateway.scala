import akka.actor._
import scala.concurrent.Future
import akka.pattern.pipe

///////////////////////////////////////////////////
//
// BqGateway Companion Object
//
///////////////////////////////////////////////////
object BqGateway {

  // Provide a Props for actor creation
  def props(bqStat: ActorRef, bqSub: ActorRef, logger: ActorRef): Props = Props(new BqGateway(bqStat,bqSub,logger))

  // BqGateway messages
//  case class Submit(script: String, options: String)
//  case class SubmitResult(requestor: ActorRef, result: BqGatewayymitResult)

}

///////////////////////////////////////////////////
//
// BqGateway Actor
//
///////////////////////////////////////////////////
class BqGateway(bqStat: ActorRef, bqSub: ActorRef, logger: ActorRef) extends Actor with RunCommand {

  import BqGateway._

  implicit val ec = context.dispatcher

  ///////////////////////////////////////////////////
  //
  // receive
  //
  ///////////////////////////////////////////////////
  def receive = {
      case _ => println("Not implemented")
//    case Submit(script,options) =>
//      val requestor = sender
//      val qsub = Future(bqBehavior.submit(script,options)) map { result => SubmitResult(requestor, result) } pipeTo self
//    case SubmitResult(requestor, result) =>
//      result.jobId match {
//        case Some(jobId) => requestor ! Transition.SubmitSucceeded(jobId)
//        case None => requestor ! Transition.SubmitFailed(result.error)
//      }
  }

}

