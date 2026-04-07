name := "oursky-todo-backend"
version := "1.0.0"
scalaVersion := "3.3.0"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-Xmax-inlines", "64"
)

libraryDependencies ++= Seq(
  // Tapir + Netty Sync (direct-style HTTP server)
  "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.13.6",
  "com.softwaremill.sttp.tapir" %% "tapir-json-upickle" % "1.13.6",
    
  // Ox - structured concurrency & direct-style runtime
  "com.softwaremill.ox" %% "core" % "1.0.4",
  "com.softwaremill.ox" %% "mdc-logback" % "1.0.4",
    
  // Magnum - direct-style database access
  "com.augustnagro" %% "magnum" % "1.3.1",
    
  // Database drivers
  "com.h2database" % "h2" % "2.2.224",
  "org.postgresql" % "postgresql" % "42.7.1",
  "com.zaxxer" % "HikariCP" % "4.0.3",
    
  // uPickle - JSON serialization
  "com.lihaoyi" %% "upickle" % "4.4.2",

  // sttp client - HTTP client for AI services
  "com.softwaremill.sttp.client4" %% "upickle" % "4.0.0",
    
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  "com.typesafe" % "config" % "1.4.3",
    
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
assembly / mainClass := Some("com.oursky.todo.Main")

// Docker configuration - use non-alpine for bash compatibility
dockerBaseImage := "eclipse-temurin:21-jre"
Docker / packageName := "todo-backend"
dockerExposedPorts ++= Seq(8080)
testFrameworks += new TestFramework("munit.Framework")