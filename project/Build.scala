// The purpose of this file is to implement the workaround to an SBT issue
// It is decribed here: https://github.com/eligosource/eventsourced/wiki/Installation#native

import sbt._
import Keys._

object MyBuild extends Build {

  lazy val myProject = Project(
    id = "myProject",
    base = file("."),
    settings = Defaults.defaultSettings ++ Seq(

      mainRunNobootcpSetting,
      testRunNobootcpSetting
    )
  )

  val runNobootcp =
    InputKey[Unit]("run-nobootcp", "Runs main classes without Scala library on the boot classpath")

  val mainRunNobootcpSetting = runNobootcp <<= runNobootcpInputTask(Runtime)
  val testRunNobootcpSetting = runNobootcp <<= runNobootcpInputTask(Test)


  def runNobootcpInputTask(configuration: Configuration) = inputTask {
    (argTask: TaskKey[Seq[String]]) => (argTask, streams, fullClasspath in configuration) map { (at, st, cp) =>
      val runCp = cp.map(_.data).mkString(":")
      val runOpts = Seq("-classpath", runCp) ++ at
      val result = Fork.java.fork(None, runOpts, None, Map(), false, LoggedOutput(st.log)).exitValue()
      if (result != 0) error("Run failed")
    }
  }
}
