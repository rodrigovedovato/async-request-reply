import Dependencies._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

resolvers += Resolver.bintrayRepo("ovotech", "maven")

val kafkaSerializationV = "0.4.1"

lazy val root = (project in file("."))
  .settings(
    mainClass in assembly := Some("com.elo7labs.AsyncRequestReply"),
    assemblyJarName in assembly := "arr-fat.jar",
    assemblyMergeStrategy in assembly := {
      case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
      case x => MergeStrategy.defaultMergeStrategy(x)
    },
    name := "async-request-reply",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.5.21",
      "org.apache.kafka" % "kafka-clients" % "2.1.1",
      "com.typesafe.akka" %% "akka-stream-kafka" % "1.0.1",
      "com.ovoenergy" %% "kafka-serialization-core" % kafkaSerializationV,
      "com.ovoenergy" %% "kafka-serialization-spray" % kafkaSerializationV,
      //"com.ovoenergy" %% "kafka-serialization-circe" % kafkaSerializationV,
      "io.circe" %% "circe-generic" % "0.11.1",
      scalaTest % Test
    )
  )