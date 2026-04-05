package com.oursky.todo

import cats.effect.*
import com.comcast.ip4s.*
import com.typesafe.config.ConfigFactory
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.CORS
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend
import slick.jdbc.H2Profile
import slick.jdbc.PostgresProfile
import com.oursky.todo.db.{Tables, TodoRepository}
import scala.concurrent.ExecutionContext

object TodoApp extends IOApp.Simple {
  def run: IO[Unit] = {
    val config = ConfigFactory.load()
    val dbType = config.getString("database.type")
    val dbUrl = config.getString("database.url")
    val dbUser = config.getString("database.user")
    val dbPassword = config.getString("database.password")

    val (profile, driverClass, dbLabel) = dbType match {
      case "h2" =>
        (H2Profile, "org.h2.Driver", s"H2 at $dbUrl")
      case "postgres" =>
        (PostgresProfile, "org.postgresql.Driver", s"PostgreSQL at $dbUrl")
      case other =>
        throw new IllegalArgumentException(s"Unsupported database type: $other. Use 'h2' or 'postgres'.")
    }

    val geminiApiKey = sys.env.getOrElse("GEMINI_API_KEY", "")
    val qwenApiKey = sys.env.getOrElse("QWEN_API_KEY", "")

    implicit val ec: ExecutionContext = ExecutionContext.global

    val db = JdbcBackend.Database.forURL(
      url = dbUrl,
      user = dbUser,
      password = dbPassword,
      driver = driverClass
    )

    val tables = new Tables(profile)
    val repo = new TodoRepository(db, tables)
    val todoService = new TodoService(repo)

    val serverResource = for {
      client <- EmberClientBuilder.default[IO].build
      _ <- Resource.eval(IO.println(s"💾 Database: $dbLabel"))
      qwenService <- if (qwenApiKey.nonEmpty) {
        Resource.pure(Some(new QwenService(client, qwenApiKey)))
      } else {
        Resource.pure(None)
      }
      geminiService <- if (geminiApiKey.nonEmpty) {
        Resource.pure(Some(new GeminiService(client, geminiApiKey)))
      } else {
        Resource.pure(None)
      }
      todoRoutes = new TodoRoutes(todoService, qwenService, geminiService)
      server <- EmberServerBuilder.default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(CORS.policy(todoRoutes.routes.orNotFound))
        .build
    } yield (server, qwenService.isDefined, geminiService.isDefined)

    serverResource.use { case (server, qwenEnabled, geminiEnabled) =>
      val aiMsg = (qwenEnabled, geminiEnabled) match {
        case (true, true)   => "🤖 AI suggestions: Qwen (primary) + Gemini (fallback)"
        case (true, false)  => "🤖 AI suggestions: Qwen only (no GEMINI_API_KEY)"
        case (false, true)  => "🤖 AI suggestions: Gemini only (no QWEN_API_KEY)"
        case (false, false) => "⚠️  No AI configured - set QWEN_API_KEY or GEMINI_API_KEY"
      }
      IO.println(s"🚀 Oursky Todo Backend started at http://${server.address.getHostString}:${server.address.getPort}") *>
      IO.println(aiMsg) *>
      IO.never
    }
  }
}
