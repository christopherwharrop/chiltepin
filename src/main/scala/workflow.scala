import scala.collection.mutable.Map
import akka.actor._
import akka.routing.RoundRobinPool

import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable
import H2DB._

import ChiltepinImplicits._

object Workflow {
  case object GetReady
  case object H2DBReady
  case object TransitionReady
  case object Run
  case object Done
  val BQGatewayID = "bqGateway"
}

class Workflow extends Actor with Stash with RunCommand with WhoAmI {

  import Workflow._
  implicit val ec = context.dispatcher
  implicit val myContext = context

  // Get owner of this process
  val whoami = whoAmI()

  class BQServer(tag: Tag) extends Table[(String, Int)](tag, "BQSERVER") {
    def host = column[String]("HOST", O.PrimaryKey) // This is the primary key column
    def port = column[Int]("PORT")
    def * = (host, port)
  }
  val bqServer = TableQuery[BQServer]

  val db = Database.forURL(s"jdbc:h2:${whoami.home}/.chiltepin/var/services;AUTO_SERVER=TRUE", driver = "org.h2.Driver")

  // Initialize children
  implicit val logger = LoggerWrapper(context.actorOf(Props[Logger], name = "logger"))
  implicit val h2DB = H2DBWrapper(context.actorOf(H2DB.props, name = "h2DB"))
  implicit var bqGateway = BQGatewayWrapper(context.system.deadLetters)

  var init: Int = 0

  // Create children before actor starts
  override def preStart() {
  
    // Retrieve the host/port of the bqServer actors from the services database
    var bqHost = ""
    var bqPort = 0
    db.withSession {
      implicit session =>
      if (MTable.getTables("BQSERVER").list().isEmpty) {
        bqServer.ddl.create
      }
      val result = bqServer.take(1).firstOption.getOrElse(("",0))
      bqHost = result._1
      bqPort = result._2
    }

    h2DB.actor ! H2DB.GetReady

    // Create bqGateway actor for submitting bq requests
    context.actorSelection(s"akka.ssl.tcp://BQGateway@$bqHost:$bqPort/user/bqGateway") ! Identify(BQGatewayID)

  }


  def uninitialized: Receive = {

    case ActorIdentity(BQGatewayID, Some(ref)) =>
      bqGateway = BQGatewayWrapper(ref)
      unstashAll()
      context.become(initialized)
    case ActorIdentity(BQGatewayID, None) => println("Didn't find bqGateway")
    case _ => stash()
  }


  def initialized: Receive = {
    case Run => 
      logger.actor ! Logger.Info("Running workflow",2)

      // Create an output place actor to supply output from a transition
      val y = context.actorOf(Props(new Place(List[String]())), name = "y")

      // Create a transition actor to run a job on input x
//      val f_of_x = context.actorOf(Props(new Transition(List("y"))), name = "f_of_x")

      // Tell the transition to fire
//      f_of_x ! Transition.Run("/home/Christopher.W.Harrop/test/test.sh")

      "test" runs "/home/Christopher.W.Harrop/test/test.sh" usingOptions "-A nesccmgmt -l procs=1 -l walltime=00:05:00" withEnvironment Map("HOME" -> "/blah/blah/home")

// jet, theia   f_of_x ! Transition.Run("/home/Christopher.W.Harrop/test/test.sh")
// yellowstone  f_of_x ! Transition.Run("/glade/u/home/harrop/test/test.sh")
// wcoss        f_of_x ! Transition.Run("/gpfs/gp1/u/Christopher.W.Harrop/test/test.sh")

    case Terminated(deadActor) =>
      logger.actor ! Logger.Info(deadActor.path.name + " has died",2)
    case Done => 
      context.system.shutdown()
  }


  def receive = uninitialized


}
