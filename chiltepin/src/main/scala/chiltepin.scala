import akka.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.io.File
import java.io.PrintWriter

import com.sun.jna.Structure
import sna.Library

object Chiltepin extends RunCommand with WhoAmI {

  def main(args: Array[String]) {

    // Get owner of this process
    val whoami = whoAmI()

    // Compute the user's chiltepin directory
    val chiltepinDir = whoami.home + "/.chiltepin"

    // Create chiltepin var dir
    val varDir = new java.io.File(chiltepinDir + "/var")
    if (! varDir.exists) varDir.mkdirs

    // Create chiltepin etc dir
    val etcDir = new java.io.File(chiltepinDir + "/etc")
    if (! etcDir.exists) etcDir.mkdirs

    // Compute the name of the user config file
    val chiltepinConfigFile = new java.io.File(chiltepinDir + "/etc/chiltepin.conf")

    // Get default configuration for the chiltepin
    val defaultConfig = ConfigFactory.load()

    // Get the current user config
    val config = if (chiltepinConfigFile.exists) {
      ConfigFactory.parseFile(chiltepinConfigFile).withFallback(defaultConfig)
    }
    else {
      defaultConfig
    }

    // Get the server mode
    val serverMode = config.getString("chiltepin.server-mode")

    // Get the workflow config for the selected server mode
    val gatewayConfig = config.getConfig(s"chiltepin.wfGateway.$serverMode").withFallback(config.getConfig("chiltepin.wfGateway")).withFallback(config.getConfig("chiltepin.workflow"))

    // Update the user config file to make sure it is up-to-date with the current options
    new PrintWriter(chiltepinDir + "/etc/chiltepin.conf") { write("chiltepin " + config.getConfig("chiltepin").root.render(ConfigRenderOptions.defaults().setOriginComments(false))); close }

    // Set up actor system
    val systemGateway = ActorSystem("WFGateway",gatewayConfig)

    // Create the Workflow actor
    val workflow = systemGateway.actorOf(Props[Workflow], name = "workflow")
  
    // Run the workflow
    workflow ! Workflow.Run

  }

}
