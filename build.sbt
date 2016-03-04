lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.11.7",
  scalacOptions ++= Seq("-feature", "-deprecation"),
//  javacOptions ++= Seq("-Xlint:unchecked"),
  resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies ++= Seq(  
    "com.typesafe.akka" %% "akka-actor" % "2.3.11",
    "com.typesafe.akka" %% "akka-remote" % "2.3.11",
    "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.11",
    "net.java.dev.jna" % "jna-platform" % "4.0.0",
    "joda-time" % "joda-time" % "2.4",
    "org.joda" % "joda-convert" % "1.6",
//    "com.typesafe.slick" % "slick_2.10" % "2.0.0",
//    "com.typesafe.slick" %% "slick" % "2.0.0",
    "com.typesafe.slick" %% "slick" % "3.1.1",
    "org.slf4j" % "slf4j-nop" % "1.6.4",
    "com.h2database" % "h2" % "1.3.166",
    "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
    // more dependencies
  )
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    // other settings
  )

lazy val bqserver = (project in file("bqserver")).
  settings(commonSettings: _*).
  settings(
    // other settings
    assemblyJarName in assembly := "bqserver-" + version.value + ".jar",
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case x if x  startsWith "com/sun/jna/Structure" => MergeStrategy.first
        case x => old(x)
      }
    }
  ).dependsOn(root)

lazy val chiltepin = (project in file("chiltepin")).
  settings(commonSettings: _*).
  settings(
    // other settings
    assemblyJarName in assembly := "chiltepin-" + version.value + ".jar",
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case x if x  startsWith "com/sun/jna/Structure" => MergeStrategy.first
        case x => old(x)
      }
    }
  ).dependsOn(root)

