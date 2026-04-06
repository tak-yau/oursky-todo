name := "oursky-todo-backend"
version := "1.0.0"
scalaVersion := "3.3.0"

val postgresqlVersion = "42.7.1"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature"
)

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor" % "1.3.0",
  "org.apache.pekko" %% "pekko-stream" % "1.3.0",
  "org.apache.pekko" %% "pekko-http" % "1.3.0",
  "org.apache.pekko" %% "pekko-slf4j" % "1.3.0",
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  "com.typesafe.slick" %% "slick" % "3.5.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
  "org.postgresql" % "postgresql" % postgresqlVersion,
  "com.h2database" % "h2" % "2.2.224",
  "org.apache.pekko" %% "pekko-testkit" % "1.3.0" % Test,
  "org.apache.pekko" %% "pekko-http-testkit" % "1.3.0" % Test,
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.apache.pekko" %% "pekko-http-spray-json" % "1.3.0",
  "org.scalatestplus" %% "mockito-5-18" % "3.2.19.0" % Test
)

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    jacocoReportSettings := JacocoReportSettings()
      .withFormats(JacocoReportFormats.HTML, JacocoReportFormats.XML)
      .withThresholds(
        JacocoThresholds(line = 70)
      ),

    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") =>
        MergeStrategy.discard
      case PathList("META-INF", "services", xs @ _*) =>
        MergeStrategy.concat
      case PathList("META-INF", "io.netty.versions.properties") =>
        MergeStrategy.first
      case PathList("META-INF", xs @ _*) if xs.last == "module-info.class" =>
        MergeStrategy.first
      case PathList("META-INF", xs @ _*) =>
        MergeStrategy.discard
      case x if x.contains("reference.conf") =>
        MergeStrategy.concat
      case x if x.contains("version.conf") =>
        MergeStrategy.last
      case x if x.endsWith(".properties") =>
        MergeStrategy.first
      case x if x.endsWith(".xml") =>
        MergeStrategy.first
      case x if x.endsWith(".class") =>
        MergeStrategy.first
      case _ =>
        MergeStrategy.first
    },

    assembly / mainClass := Some("com.oursky.todo.TodoApp"),

    dockerBaseImage := "eclipse-temurin:17-jre",
    Docker / packageName := "todo-backend",
    dockerExposedPorts ++= Seq(8080)
  )
