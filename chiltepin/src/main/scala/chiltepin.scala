import akka.actor._
import com.typesafe.config.ConfigFactory
import java.io.File

object Chiltepin {

  def main(args: Array[String]) {

    // Get configuration for the bqserver
    val configFile = getClass.getClassLoader.getResource("chiltepin.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile))

    // Set up actor system
    val system = ActorSystem("Chiltepin",config)

    // Create the Workflow actor
    val workflow = system.actorOf(Props[Workflow], name = "workflow")
  
    // Run the workflow
    workflow ! Workflow.Run

  }

}
