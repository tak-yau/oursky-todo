package com.oursky.todo

import cats.effect.*
import com.comcast.ip4s.*
import com.typesafe.config.ConfigFactory
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.CORS
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api._
import com.oursky.todo.db.{Tables, TodoRepository}
import scala.concurrent.ExecutionContext

object TodoApp extends IOApp.Simple {
  def run: IO[Unit] = {
    val config = ConfigFactory.load()
    val dbUrl = config.getString("database.url")

    val apiKey = sys.env.getOrElse("GEMINI_API_KEY", "")

    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val profile: H2Profile.type = H2Profile

    val db = H2Profile.backend.Database.forURL(
      url = dbUrl,
      driver = "org.h2.Driver"
    )

    val tables = new Tables(H2Profile)
    val repo = new TodoRepository(db, tables)
    val todoService = new TodoService(repo)

    val initializeSchema: IO[Unit] =
      IO.fromFuture(IO(db.run(tables.schema.create))).void
        .handleErrorWith(_ => IO.unit)

    val serverResource = for {
      client <- EmberClientBuilder.default[IO].build
      _ <- Resource.eval(IO.println(s"💾 Database: H2 at $dbUrl"))
      _ <- Resource.eval(initializeSchema)
      _ <- Resource.eval(IO.println(s"📊 Database schema initialized"))
      geminiService <- if (apiKey.nonEmpty) {
        Resource.pure(Some(new GeminiService(client, apiKey)))
      } else {
        Resource.pure(None)
      }
      todoRoutes = new TodoRoutes(todoService, geminiService)
      server <- EmberServerBuilder.default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(CORS.policy(todoRoutes.routes.orNotFound))
        .build
    } yield (server, geminiService.isDefined)

    serverResource.use { case (server, aiEnabled) =>
      val aiMsg = if (aiEnabled) "🤖 AI suggestions enabled" else "⚠️  GEMINI_API_KEY not set - AI suggestions disabled"
      IO.println(s"🚀 Oursky Todo Backend started at http://${server.address.getHostString}:${server.address.getPort}") *>
      IO.println(aiMsg) *>
      IO.never
    }
  }
}
