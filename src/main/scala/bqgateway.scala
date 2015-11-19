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
  case class Submit(script: String, options: String)
  case class WatchJob(requestor: ActorRef,jobId: String)
  case class StatusRequest(jobId: String)
  case class UnwatchJob(requestor: ActorRef,jobId: String)

}

///////////////////////////////////////////////////
//
// BqGateway Actor
//
///////////////////////////////////////////////////
class BqGateway(bqStat: ActorRef, bqSub: ActorRef, logger: ActorRef) extends Actor with RunCommand {

  import BqGateway._
  import BqStat._
  import BqSub._

  implicit val ec = context.dispatcher

  ///////////////////////////////////////////////////
  //
  // receive
  //
  ///////////////////////////////////////////////////
  def receive = {
      case BqGateway.Submit(script,options) => bqSub forward BqSub.Submit(script,options)
      case BqGateway.WatchJob(subscriber,jobid) => bqStat forward BqStat.WatchJob(subscriber,jobid)
      case BqGateway.StatusRequest(jobId) => bqStat forward BqStat.StatusRequest(jobId)
      case BqGateway.UnwatchJob(subscriber,jobid) => bqStat forward BqStat.UnwatchJob(subscriber,jobid)

      case _ => println("Not implemented")
  }

}

