import akka.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.io.File
import java.io.PrintWriter
import akka.routing.FromConfig
import akka.actor.{ Address, AddressFromURIString }
import scala.slick.driver.H2Driver.simple._
import scala.slick.jdbc.meta.MTable

// Extension needed to get full remote actor path
object ExternalAddress extends ExtensionKey[ExternalAddressExt]
 class ExternalAddressExt(system: ExtendedActorSystem) extends Extension {
  def addressForAkka: Address = system.provider.getDefaultAddress
}

object BQServer extends WhoAmI {

  def main(args: Array[String]) {

    // Get owner of this process
    val whoami = whoAmI()

    // Set up database access
    class BQServer(tag: Tag) extends Table[(String, Int)](tag, "BQSERVER") {
      def host = column[String]("HOST", O.PrimaryKey) // This is the primary key column
      def port = column[Int]("PORT")
      def * = (host, port)
    }
    val bqServer = TableQuery[BQServer]

    // Get default configuration for the bqserver
    val defaultConfig = ConfigFactory.load()

    // Compute the user's chiltepin directory
    val chiltepinDir = whoami.home + "/.chiltepin"

    // Create chiltepin var dir
    val varDir = new java.io.File(chiltepinDir + "/var")
    if (! varDir.exists) varDir.mkdirs

    // Create chiltepin etc dir
    val etcDir = new java.io.File(chiltepinDir + "/etc")
    if (! etcDir.exists) etcDir.mkdirs

    // Compute the name of the user config file
    val bqServerConfigFile = new java.io.File(chiltepinDir + "/etc/bqserver.conf")

    // Get the current user config
    val userConfig = if (bqServerConfigFile.exists) {
      ConfigFactory.parseFile(bqServerConfigFile)
    }
    else {
      defaultConfig.getConfig("bqserver")
    }

    // Load the user's config merged with default config
    val config = ConfigFactory.load(userConfig.withFallback(defaultConfig))
    val gatewayConfig = ConfigFactory.load(userConfig.withFallback(defaultConfig.getConfig("bqgateway")).withFallback(config))
    val workerConfig = ConfigFactory.load(userConfig.withFallback(defaultConfig.getConfig("bqworker")).withFallback(config))

    // Update the user config file to make sure it is up-to-date with the current options
    new PrintWriter(chiltepinDir + "/etc/bqserver.conf") { write("bqserver " + config.getConfig("bqserver").root.render(ConfigRenderOptions.defaults().setOriginComments(false))); close }

    // Instantiate the configured BQServer behavior
    val bqServerType = config.getString("bqserver.type")
    val bqBehavior = bqServerType.toUpperCase match {
      case "TORQUE" => new TorqueBehavior
      case "MOAB-TORQUE" => new MoabTorqueBehavior
      case "LSF" => new LSFBehavior
      case _ => throw new RuntimeException(s"Unknown batch system: $bqServerType")
    }

    // Set up back end actor system for batch system services
    val systemWorker = ActorSystem("BQWorker", workerConfig)

    // Set up back end actor system for batch system services
    val systemServer = ActorSystem("BQServer", gatewayConfig)

    // Get actor system's hostname and port number
    val host = ExternalAddress(systemServer).addressForAkka.host.getOrElse("")
    val port = ExternalAddress(systemServer).addressForAkka.port.getOrElse(0)
    val address = Seq(AddressFromURIString(s"akka.ssl.tcp://BQServer@$host:$port"))

    // Record actor system's host/port in the services database
    val db = Database.forURL(s"jdbc:h2:${whoami.home}/.chiltepin/var/services;AUTO_SERVER=TRUE", driver = "org.h2.Driver")
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
    val logger = systemWorker.actorOf(Props(new Logger), name = "logger")

    // Create the BqStat actor
    val bqStat = systemWorker.actorOf(BqStat.props(bqBehavior, logger), "bqStat")

    // Create the BqSub router pool
    val bqSub = systemWorker.actorOf(FromConfig.props(BqSub.props(bqBehavior, logger)), "bqSub")

    // Create the bqGateway actor
    val bqGateway = systemServer.actorOf(BqGateway.props(bqStat, bqSub, logger), "bqGateway")


  }

}
