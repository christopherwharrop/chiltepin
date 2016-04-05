import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor._
import akka.routing.RoundRobinPool

import slick.driver.H2Driver.api._
import slick.lifted.Tag
import slick.jdbc.meta.MTable

import H2DB._

//import ChiltepinImplicits._

object WFGateway {
  case object GetReady
  case object H2DBReady
  case object TransitionReady
  case object Run
  case object Done
  val BQGatewayID = "bqGateway"
}

class WFGateway extends Actor with Stash with RunCommand with WhoAmI {

  import WFGateway._
  implicit val ec = context.dispatcher
//  implicit val myContext = context

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

//  var init: Int = 0

  // Start the WFGateway
  def go(): Unit = {
    context.actorSelection("/user/WFGateway/wfGateway/*") ! Transition.Go
  }
  
  // Create children before actor starts
  override def preStart() {

    println("WFGateway preStart() begin")  
    // Retrieve the host/port of the bqServer actors from the services database
    var bqHost = ""
    var bqPort = 0

    if (Await.result(db.run(MTable.getTables("BQSERVER")), 1.seconds).isEmpty) {
      val setupAction : DBIO[Unit] = DBIO.seq(bqServer.schema.create)
      val setupFuture : Future[Unit] = db.run(setupAction)
      Await.result(setupFuture,1.seconds)
    }

    val queryAction = bqServer.result.headOption
    val queryFuture = db.run(queryAction)
    val result = Await.result(queryFuture, 1.seconds).getOrElse(("",0))
      
    bqHost = result._1
    bqPort = result._2

    h2DB.actor ! H2DB.GetReady

//    for ((name,task) <- Workflow.tasks) {
//      context.actorOf(Transition.props(task), name = name)// ! Transition.Go
//    }

    // Create bqGateway actor for submitting bq requests
    context.actorSelection(s"akka.ssl.tcp://BQGateway@$bqHost:$bqPort/user/bqGateway") ! Identify(BQGatewayID)

    println("WFGateway preStart() end")  
  }


  def uninitialized: Receive = {

    case ActorIdentity(BQGatewayID, Some(ref)) =>
      bqGateway = BQGatewayWrapper(ref)
      unstashAll()
      println("Acquired connection to bqGateway")
      context.become(initialized)
    case ActorIdentity(BQGatewayID, None) => println("Did not find bqGateway")
    case _ => 
      stash()
      println("Stashing message during initialization")
  }


  def initialized: Receive = {
    case Run => 

      // Set the options to use 
//      val wcossOpt =       "-P HWRF-T2O -W 00:01 -n 1 -q debug -J chiltepin"
//      val yellowstoneOpt = "-P P48500053 -W 00:01 -n 1 -q caldera -J chiltepin"
//      val jetOpt =         "-A jetmgmt -l procs=1,partition=njet,walltime=00:05:00 -N chiltepin"
//      val theiaOpt =       "-A nesccmgmt -l procs=1,walltime=00:05:00"
//      val options = jetOpt

      // Set the command to use
//      val wcossCmd = "/gpfs/gp1/u/Christopher.W.Harrop/test/test.sh"
//      val yellowstoneCmd = "/glade/u/home/harrop/test/test.sh"
//      val jetCmd = "/home/Christopher.W.Harrop/test/test.sh"
//      val theiaCmd = "/home/Christopher.W.Harrop/test/test.sh"
//      val command = jetCmd

//      "test1" runs command usingOptions options withEnvironment Map("FOO" -> "/blah/blah/foo1")
//      "test2" usingOptions options runs command withEnvironment Map("FOO" -> "/blah/blah/foo2") dependsOn "test1"
//      "test3" usingOptions options runs command withEnvironment Map("FOO" -> "/blah/blah/foo2") dependsOn "test2"

   

 for ((name,task) <- Workflow.tasks) {
      context.actorOf(Transition.props(task), name = name) ! Transition.Go
    }

//      go

    case Terminated(deadActor) =>
      logger.actor ! Logger.Info(deadActor.path.name + " has died",2)
    case Done => 
      context.system.shutdown()
  }


  def receive = uninitialized


}
