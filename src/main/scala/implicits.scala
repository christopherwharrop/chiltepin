import scala.collection.mutable.Map
import scala.language.implicitConversions
import akka.actor._

object ChiltepinImplicits {

  class TransitionHelper(transition: ActorRef) {

    // Set the command to run
    def runs(cmd: String): TransitionHelper = {
      transition ! Transition.SetCommand(cmd)
      this
    }
    // Set the options to use when running the command
    def usingOptions(opt: String): TransitionHelper = {
      transition ! Transition.SetOptions(opt)
      this
    }
    // Set the environment to use when running the command
    def withEnvironment(env: Map[String,String]) = {
      transition ! Transition.SetEnvironment(env)
      transition ! Transition.Run
    }      

  }
  implicit def String2TransitionHelper(transition: String)(implicit context: ActorContext, logger: LoggerWrapper, h2DB: H2DBWrapper, bqGateway: BQGatewayWrapper) = new TransitionHelper(context.actorOf(Props(new Transition(List("y"))), name = transition))

}