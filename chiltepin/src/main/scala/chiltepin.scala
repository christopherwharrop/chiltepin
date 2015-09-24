import akka.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.io.File
import java.io.PrintWriter

object Chiltepin {

  def main(args: Array[String]) {

    // Get default configuration for the chiltepin
    val defaultConfig = ConfigFactory.load()

    // Compute the user's chiltepin directory
    val chiltepinDir = sys.env("HOME") + "/.chiltepin"

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
      ConfigFactory.parseFile(chiltepinConfigFile)
    }
    else {
      defaultConfig.getConfig("chiltepin")
    }

    // Load the user's config merged with default config
    val config = ConfigFactory.load(userConfig.withFallback(defaultConfig))

    // Update the user config file to make sure it is up-to-date with the current options
    new PrintWriter(chiltepinDir + "/etc/chiltepin.conf") { write("chiltepin " + config.getConfig("chiltepin").root.render(ConfigRenderOptions.defaults().setOriginComments(false))); close }

    // Set up actor system
    val system = ActorSystem("Chiltepin",config)

    // Create the Workflow actor
    val workflow = system.actorOf(Props[Workflow], name = "workflow")
  
    // Run the workflow
    workflow ! Workflow.Run

  }

}
