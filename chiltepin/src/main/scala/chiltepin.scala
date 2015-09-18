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

    // Update the config if needed
    val chiltepinConfigFile = new java.io.File(chiltepinDir + "/etc/chiltepin.conf")
    if (! chiltepinConfigFile.exists) {
      new PrintWriter(chiltepinDir + "/etc/chiltepin.conf") { write("chiltepin " + defaultConfig.getConfig("chiltepin").root.render(ConfigRenderOptions.defaults().setOriginComments(false))); close }
    }

    // Load the user's config
    val userConfig = ConfigFactory.parseFile(chiltepinConfigFile)

    // Load the user's config merged with default config
    val config = ConfigFactory.load(userConfig.withFallback(defaultConfig))

    // Set up actor system
    val system = ActorSystem("Chiltepin",config)

    // Create the Workflow actor
    val workflow = system.actorOf(Props[Workflow], name = "workflow")
  
    // Run the workflow
    workflow ! Workflow.Run

  }

}
