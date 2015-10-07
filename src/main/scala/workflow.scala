import akka.actor._
import akka.routing.RoundRobinPool

import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable
import H2DB._


object Workflow {
  case object GetReady
  case object H2DBReady
  case object BqStatReady
  case object BqSubReady
  case object TransitionReady
  case object Run
  case object Done
  val BQSubID = "bqSub"
  val BQStatID = "bqStat"
}

class Workflow extends Actor with Stash {

  import Workflow._
  implicit val ec = context.dispatcher


  class BQServer(tag: Tag) extends Table[(String, Int)](tag, "BQSERVER") {
    def host = column[String]("HOST", O.PrimaryKey) // This is the primary key column
    def port = column[Int]("PORT")
    def * = (host, port)
  }
  val bqServer = TableQuery[BQServer]

  val db = Database.forURL("jdbc:h2:/home/Christopher.W.Harrop/.chiltepin/var/services;AUTO_SERVER=TRUE", driver = "org.h2.Driver")


  // Initialize children
  var logger: ActorRef = context.system.deadLetters
  var h2DB: ActorRef = context.system.deadLetters
  var bqStat: ActorRef = context.system.deadLetters
  var bqSub: ActorRef = context.system.deadLetters
  var x: ActorRef = context.system.deadLetters
  var f_of_x: ActorRef = context.system.deadLetters
  var g_of_x: ActorRef = context.system.deadLetters
  var y: ActorRef = context.system.deadLetters
  var z: ActorRef = context.system.deadLetters

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


    // Create a logger actor for logging workflow events
    logger = context.actorOf(Props[Logger], name = "logger")

    // Create h2DB actor for handling database queries and updates
    h2DB = context.actorOf(Props(new H2DB(logger)), name = "h2DB")
    h2DB ! H2DB.GetReady

    // Create bqstat actor for tracking job state
    context.actorSelection(s"akka.ssl.tcp://BQServer@$bqHost:$bqPort/user/bqStat") ! Identify(BQStatID)

    // Create bqsub actor for submitting jobs 
    context.actorSelection(s"akka.ssl.tcp://BQServer@$bqHost:$bqPort/user/bqSub") ! Identify(BQSubID)

  }


  def uninitialized: Receive = {

    case ActorIdentity(BQSubID, Some(ref)) => 
        bqSub = ref
        context.watch(bqSub)
        init = init + 1
        if (init == 3) {
          unstashAll()
          context.become(initialized)
        }
    case ActorIdentity(BQSubID, None) => println("Didn't find bqsub")
    case ActorIdentity(BQStatID, Some(ref)) => 
        bqStat = ref
        context.watch(bqStat)
        init = init + 2
        if (init == 3) {
          unstashAll()
          context.become(initialized)
        }
    case ActorIdentity(BQStatID, None) => println("Didn't find bqstat")
    case _ => stash()
  }


  def initialized: Receive = {
    case Run => 
      logger ! Logger.Info("Running workflow",2)

    // Create an input place actor to supply input to a transition
//    x = context.actorOf(Props(new Place(h2DB,logger,List("f_of_x","g_of_x"))), name = "x")

    // Create an output place actor to supply output from a transition
    y = context.actorOf(Props(new Place(h2DB,logger,List[String]())), name = "y")

    // Create a transition actor to run a job on input x
    f_of_x = context.actorOf(Props(new Transition(h2DB,bqStat,bqSub,logger,List("y"))), name = "f_of_x")

    // Create a transition actor to run a job on input x
//    g_of_x = context.actorOf(Props(new Transition(h2DB,bqStat,bqSub,logger,List("z"))), name = "g_of_x")


    // Create an output place actor to supply output from a transition
//    z = context.actorOf(Props(new Place(h2DB,logger,List[String]())), name = "z")



      f_of_x ! Transition.Run("/home/Christopher.W.Harrop/test/test.sh")
    case Terminated(deadActor) =>
      println(deadActor.path.name + " has died")
    case Done => 
      context.system.shutdown()
  }


  def receive = uninitialized


}
