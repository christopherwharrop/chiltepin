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

    // Get default configuration for the chiltepin
    val defaultConfig = ConfigFactory.load().getConfig("chiltepin")

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

    // Get the current user config
    val userConfig = if (chiltepinConfigFile.exists) {
      ConfigFactory.parseFile(chiltepinConfigFile).getConfig("chiltepin")
    }
    else {
      defaultConfig
    }

    // Load the user's config merged with default config
    val config = ConfigFactory.load(userConfig.withFallback(defaultConfig))
    val workflowConfig = ConfigFactory.load(userConfig.withFallback(defaultConfig.getConfig("workflow")).withFallback(config))

    // Update the user config file to make sure it is up-to-date with the current options
    new PrintWriter(chiltepinDir + "/etc/chiltepin.conf") { write("chiltepin " + config.getConfig("chiltepin").root.render(ConfigRenderOptions.defaults().setOriginComments(false))); close }

    // Set up actor system
    val system = ActorSystem("Chiltepin",workflowConfig)

    // Create the Workflow actor
    val workflow = system.actorOf(Props[Workflow], name = "workflow")
  
    // Run the workflow
    workflow ! Workflow.Run

  }

}
