import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.io.File
import java.io.PrintWriter
import akka.routing.FromConfig
import akka.actor.{ Address, AddressFromURIString }

import slick.driver.H2Driver.api._
import slick.lifted.Tag
import slick.jdbc.meta.MTable


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

    // Compute the user's chiltepin directory
    val chiltepinDir = whoami.home + "/.chiltepin"

    // Create chiltepin var dir
    val varDir = new java.io.File(chiltepinDir + "/var")
    if (! varDir.exists) varDir.mkdirs

    // Create chiltepin etc dir
    val etcDir = new java.io.File(chiltepinDir + "/etc")
    if (! etcDir.exists) etcDir.mkdirs

    // Compute the name of the user config file
    val bqServerConfigFile = new java.io.File(chiltepinDir + "/etc/chiltepin.conf")

    // Get default configuration for the chiltepin
    val defaultConfig = ConfigFactory.load()

    // Get the current user config
    val config = if (bqServerConfigFile.exists) {
      ConfigFactory.parseFile(bqServerConfigFile).withFallback(defaultConfig)
    }
    else {
      defaultConfig
    }

    // Get the server mode
    val serverMode = config.getString("chiltepin.server-mode")

    // Get the workflow config for the selected server mode
    val gatewayConfig = config.getConfig(s"chiltepin.bqgateway.$serverMode").withFallback(config.getConfig("chiltepin.bqgateway"))

    // Load the user's config merged with default config
    val workerConfig = config.getConfig("chiltepin.bqworker").withFallback(config.getConfig("chiltepin.bqserver"))

    // Update the user config file to make sure it is up-to-date with the current options
    new PrintWriter(chiltepinDir + "/etc/chiltepin.conf") { write("chiltepin " + config.getConfig("chiltepin").root.render(ConfigRenderOptions.defaults().setOriginComments(false))); close }

    // Instantiate the configured BQServer behavior
    val bqServerType = config.getString("chiltepin.bqserver.batch-system")
    val bqBehavior = bqServerType.toUpperCase match {
      case "TORQUE" => new TorqueBehavior
      case "MOAB-TORQUE" => new MoabTorqueBehavior
      case "LSF" => new LSFBehavior
      case _ => throw new RuntimeException(s"Unknown batch system: $bqServerType")
    }

    // Set up back end actor system for batch system services
    val systemWorker = ActorSystem("BQWorker", workerConfig)

    // Set up back end actor system for batch system services
    val systemGateway = ActorSystem("BQGateway", gatewayConfig)

    // Get actor system's hostname and port number
    val host = ExternalAddress(systemGateway).addressForAkka.host.getOrElse("")
    val port = ExternalAddress(systemGateway).addressForAkka.port.getOrElse(0)
    val address = Seq(AddressFromURIString(s"akka.ssl.tcp://BQGateway@$host:$port"))

    // Record actor system's host/port in the services database
    val db = Database.forURL(s"jdbc:h2:${whoami.home}/.chiltepin/var/services;AUTO_SERVER=TRUE", driver = "org.h2.Driver")
//    db.withSession {
//      implicit session =>
//      if (MTable.getTables("BQSERVER").list().isEmpty) {
//        bqServer.ddl.create
//        bqServer += (host, port)
//      } else {
//        val q = for { c <- bqServer } yield (c.host,c.port)
//        q.update(host, port)
//      }
//    }
    
    if (Await.result(db.run(MTable.getTables("BQSERVER")), 1.seconds).isEmpty) {
      val setupAction : DBIO[Unit] = DBIO.seq(bqServer.schema.create, bqServer ++= Seq((host, port)))
      val setupFuture : Future[Unit] = db.run(setupAction)
      Await.result(setupFuture,1.seconds)
    } else {
      val oldEntry = for { bqs <- bqServer} yield (bqs.host, bqs.port)
      val updateAction = oldEntry.update(host, port)
      val updateFuture = db.run(updateAction)
      Await.result(updateFuture, 1.seconds)
    }

    // Create the logging actor
    val logger = systemWorker.actorOf(Props(new Logger), name = "logger")

    // Create the BqStat actor
    val bqStat = systemWorker.actorOf(BqStat.props(bqBehavior, logger), "bqStat")

    // Create the BqSub router pool
    val bqSub = systemWorker.actorOf(FromConfig.props(BqSub.props(bqBehavior, logger)), "bqSub")

    // Create the bqGateway actor
    val bqGateway = systemGateway.actorOf(BqGateway.props(bqStat, bqSub, logger), "bqGateway")


  }

}
