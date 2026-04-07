name := "oursky-todo-backend"
version := "1.0.0"
scalaVersion := "3.3.0"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature"
)

libraryDependencies ++= Seq(
  // Tapir + Netty Sync (direct-style server)
  "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.13.6",
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.13.6",
  
  // Database dependencies
  "com.typesafe.slick" %% "slick" % "3.5.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
  "com.h2database" % "h2" % "2.2.224",
  "org.postgresql" % "postgresql" % "42.7.1",
  
  // JSON
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  
  // Test dependencies
  "org.scalameta" %% "munit" % "0.7.29" % Test
)

// Enable plugins
enablePlugins(JavaAppPackaging, DockerPlugin)

// Assembly configuration for fat JAR
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF") =>
    MergeStrategy.discard
  case PathList("META-INF", "services", xs @ _*) =>
    MergeStrategy.concat
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.first
  case PathList("META-INF", xs @ _*) =>
    MergeStrategy.discard
  case x if x.endsWith(".properties") =>
    MergeStrategy.first
  case x if x.endsWith(".xml") =>
    MergeStrategy.first
  case _ =>
    MergeStrategy.first
}
assembly / mainClass := Some("com.oursky.todo.TodoApp")

// Docker configuration - use non-alpine for bash compatibility
dockerBaseImage := "eclipse-temurin:17-jre"
Docker / packageName := "todo-backend"
dockerExposedPorts ++= Seq(8080)
testFrameworks += new TestFramework("munit.Framework")
