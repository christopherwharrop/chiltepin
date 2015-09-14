import akka.actor._
import com.typesafe.config.ConfigFactory
import java.io.File
import akka.routing.FromConfig
import akka.actor.{ Address, AddressFromURIString }
import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable

// Extension needed to get full remote actor path
object ExternalAddress extends ExtensionKey[ExternalAddressExt]
 class ExternalAddressExt(system: ExtendedActorSystem) extends Extension {
  def addressForAkka: Address = system.provider.getDefaultAddress
}

object BQServer {

  def main(args: Array[String]) {

    // Set up database access
    class BQServer(tag: Tag) extends Table[(String, Int)](tag, "BQSERVER") {
      def host = column[String]("HOST", O.PrimaryKey) // This is the primary key column
      def port = column[Int]("PORT")
      def * = (host, port)
    }
    val bqServer = TableQuery[BQServer]

    // Get configuration for the bqserver
    val configFile = getClass.getClassLoader.getResource("bqserver.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile)).resolve()

    // Instantiate the configured BQServer behavior
    val bqServerType = config.getString("bqserver.type")
    val bqBehavior = bqServerType.toUpperCase match {
      case "MOAB-TORQUE" => new MoabTorqueBehavior
      case _ => throw new RuntimeException(s"Unknown batch system: $bqServerType")
    }

    // Set up actor system for batch system services
    val system = ActorSystem("BQServer", config)

    // Get actor system's hostname and port number
    val host = ExternalAddress(system).addressForAkka.host.getOrElse("")
    val port = ExternalAddress(system).addressForAkka.port.getOrElse(0)
    val address = Seq(AddressFromURIString(s"akka.tcp://BQServer@$host:$port"))

    // Record actor system's host/port in the services database
    val db = Database.forURL("jdbc:h2:/home/Christopher.W.Harrop/.chiltepin/var/services", driver = "org.h2.Driver")
    db.withSession {
      implicit session =>
      if (MTable.getTables("BQSERVER").list().isEmpty) {
        bqServer.ddl.create
        bqServer += (host, port)
      } else {
        val q = for { c <- bqServer } yield (c.host,c.port)
        q.update(host, port)
      }
    }

    // Create the logging actor
    val logger = system.actorOf(Props(new Logger), name = "logger")

    // Create the BqStat actor
    val bqStat = system.actorOf(BqStat.props(bqBehavior, logger), "bqStat")

    // Create the BqSub router pool
    val bqSub = system.actorOf(FromConfig.props(BqSub.props(bqBehavior, logger)), "bqSub")

  }

}
