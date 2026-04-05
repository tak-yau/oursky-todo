name := "oursky-todo-backend"
version := "1.0.0"
scalaVersion := "3.3.0"

// No inline limit needed with semi-auto derivation
scalacOptions ++= Seq(
  "-deprecation",
  "-feature"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.5.1",
  "org.http4s" %% "http4s-dsl" % "0.23.23",
  "org.http4s" %% "http4s-ember-server" % "0.23.23",
  "org.http4s" %% "http4s-ember-client" % "0.23.23",
  "org.http4s" %% "http4s-circe" % "0.23.23",
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  "com.comcast" %% "ip4s-core" % "3.3.0",
  "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  // Database dependencies
  "com.typesafe.slick" %% "slick" % "3.5.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
  "com.h2database" % "h2" % "2.2.224",
  "org.postgresql" % "postgresql" % "42.7.1",
  // Test dependencies
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0" % Test
)

// Enable plugins
enablePlugins(JavaAppPackaging, DockerPlugin)

// Assembly configuration for fat JAR
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) =>
    MergeStrategy.discard
  case x if x.endsWith(".MF") =>
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
