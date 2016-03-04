import akka.actor._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}
//import scala.slick.driver.H2Driver.simple._
//import scala.slick.jdbc.meta.MTable

import slick.driver.H2Driver.api._
import slick.lifted.Tag
import slick.jdbc.meta.MTable
//import scala.concurrent.ExecutionContext.Implicits.global

case class H2DBWrapper(actor: ActorRef)

object H2DB {

  // Provide a Props for actor creation
  def props(implicit logger: LoggerWrapper): Props = { Props(new H2DB) }

  case object GetReady
  case class AddJob(id: String, state: String)
  case class UpdateJob(id: String, state: String)
  case object GetJobs

}

class H2DB(implicit logger: LoggerWrapper) extends Actor {

  import H2DB._
  implicit val ec = context.dispatcher

  val db = Database.forURL("jdbc:h2:~/test/test;AUTO_SERVER=TRUE", driver = "org.h2.Driver")

  class Jobs(tag: Tag) extends Table[(String, String)](tag, "JOBS") {
    def id = column[String]("JOBID", O.PrimaryKey) // This is the primary key column
    def state = column[String]("STATE")
    def * = (id, state)
  }
  val jobs = TableQuery[Jobs]

  override def preStart() {
//    context.system.scheduler.schedule(100.millis, 5.seconds, self, GetJobs)
  }

  def receive = {
    case GetReady => 
//      db.withSession {
//        implicit session =>
//        if (MTable.getTables("JOBS").list().isEmpty) {
//          jobs.ddl.create
//        }
//      }

      if (Await.result(db.run(MTable.getTables("JOBS")), 1.seconds).isEmpty) {
        val setupAction : DBIO[Unit] = DBIO.seq(jobs.schema.create)
        val setupFuture : Future[Unit] = db.run(setupAction)
        Await.result(setupFuture,1.seconds)
      }
      sender ! Workflow.H2DBReady
    case AddJob(jobid,state) =>
//      db.withSession {
//        implicit session =>
//        jobs += (jobid,state)
//      }

      val populateAction: DBIO[Option[Int]] = jobs ++= Seq((jobid,state))
      val populateFuture : Future[Option[Int]] = db.run(populateAction)
      Await.result(populateFuture,1.seconds)

    case GetJobs =>
//      db.withSession {
//        implicit session =>
//        jobs foreach { case (id, state) =>
//           logger.actor ! Logger.Info(s"Database shows job $id in state $state",2)
//         }       
//      }


      db.run(jobs.result).map(_.foreach {
        case (id, state) =>
           logger.actor ! Logger.Info(s"Database shows job $id in state $state",2)
      })


    case UpdateJob(id,newstate) =>
//      db.withSession {
//        implicit session =>
//        val oldstate = for { job <- jobs if job.id === id } yield job.state
//        oldstate.update(newstate)
//      }

      val oldstate = for { job <- jobs if job.id === id } yield job.state
      val updateAction = oldstate.update(newstate)
      val updateFuture = db.run(updateAction)
      Await.result(updateFuture, 1.seconds)
   
  }

}
