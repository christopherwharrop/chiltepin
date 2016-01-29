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

  // Start the workflow
  def go(): Unit = {
    context.actorSelection("/user/workflow/*") ! Transition.Go
  }

  

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

      // Set the options to use 
      val wcossOpt =       "-P HWRF-T2O -W 00:01 -n 1 -q debug -J chiltepin"
      val yellowstoneOpt = "-P P48500053 -W 00:01 -n 1 -q caldera -J chiltepin"
      val jetOpt =         "-A jetmgmt -l procs=1,partition=njet,walltime=00:05:00 -N chiltepin"
      val theiaOpt =       "-A nesccmgmt -l procs=1,walltime=00:05:00"
      val options = jetOpt

      // Set the command to use
      val wcossCmd = "/gpfs/gp1/u/Christopher.W.Harrop/test/test.sh"
      val yellowstoneCmd = "/glade/u/home/harrop/test/test.sh"
      val jetCmd = "/home/Christopher.W.Harrop/test/test.sh"
      val theiaCmd = "/home/Christopher.W.Harrop/test/test.sh"
      val command = jetCmd

      "test1" runs command usingOptions options withEnvironment Map("FOO" -> "/blah/blah/foo1")
      "test2" usingOptions options runs command withEnvironment Map("FOO" -> "/blah/blah/foo2") dependsOn "test1"

      go

    case Terminated(deadActor) =>
      logger.actor ! Logger.Info(deadActor.path.name + " has died",2)
    case Done => 
      context.system.shutdown()
  }


  def receive = uninitialized


}
