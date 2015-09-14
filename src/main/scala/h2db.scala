import akka.actor._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable

object H2DB {
  case object GetReady
  case class AddJob(id: String, state: String)
  case class UpdateJob(id: String, state: String)
  case object GetJobs
}

class H2DB(logger: ActorRef) extends Actor {

  import H2DB._
  implicit val ec = context.dispatcher

  val db = Database.forURL("jdbc:h2:~/test/test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

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
      db.withSession {
        implicit session =>
        if (MTable.getTables("JOBS").list().isEmpty) {
          jobs.ddl.create
        }
      }
      sender ! Workflow.H2DBReady
    case AddJob(jobid,state) =>
      db.withSession {
        implicit session =>
        jobs += (jobid,state)
      }
    case GetJobs =>
      db.withSession {
        implicit session =>
        jobs foreach { case (id, state) =>
           logger ! Logger.Info(s"Database shows job $id in state $state",2)
         }       
      }
    case UpdateJob(id,newstate) =>
      db.withSession {
        implicit session =>
        val oldstate = for { job <- jobs if job.id === id } yield job.state
        oldstate.update(newstate)
      }
   
  }

}
