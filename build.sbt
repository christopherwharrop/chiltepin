lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.10.4",
  scalacOptions ++= Seq("-feature", "-deprecation"),
  resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies ++= Seq(  
    "com.typesafe.akka" %% "akka-actor" % "2.3.11",
    "com.typesafe.akka" %% "akka-remote" % "2.3.11",
    "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.11",
    "joda-time" % "joda-time" % "2.4",
    "org.joda" % "joda-convert" % "1.6",
    "com.typesafe.slick" %% "slick" % "2.0.0",
    "org.slf4j" % "slf4j-nop" % "1.6.4",
    "com.h2database" % "h2" % "1.3.166"
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
  ).dependsOn(root)

lazy val chiltepin = (project in file("chiltepin")).
  settings(commonSettings: _*).
  settings(
    // other settings
  ).dependsOn(root)

