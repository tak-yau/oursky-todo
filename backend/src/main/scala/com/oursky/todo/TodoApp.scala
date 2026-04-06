package com.oursky.todo

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import com.typesafe.config.ConfigFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.H2Profile
import slick.jdbc.PostgresProfile
import com.oursky.todo.db.{Tables, TodoRepository}
import com.oursky.todo.actors.{GuardianActor, AISuggestionActor}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object TodoApp {
  def main(args: Array[String]): Unit = {

    if (args.contains("--health")) {
      val conn = new java.net.URL("http://localhost:8080/health").openConnection().asInstanceOf[java.net.HttpURLConnection]
      conn.setRequestMethod("GET")
      conn.setConnectTimeout(3000)
      conn.setReadTimeout(3000)
      try {
        val code = conn.getResponseCode()
        if (code == 200) sys.exit(0) else sys.exit(1)
      } catch {
        case _: Exception => sys.exit(1)
      } finally {
        conn.disconnect()
      }
    }

    val system: ActorSystem = ActorSystem("todo-app")
    implicit val mat: Materializer = Materializer(system)

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

    val db = JdbcBackend.Database.forURL(
      url = dbUrl,
      user = dbUser,
      password = dbPassword,
      driver = driverClass
    )

    println(s"💾 Database: $dbLabel")

    val tables = new Tables(profile)
    Await.result(db.run(tables.createSchema), 10.seconds)

    val repo = new TodoRepository(db, tables)

    val qwenService = if (qwenApiKey.nonEmpty) {
      Some(new QwenService(qwenApiKey)(system, mat))
    } else {
      None
    }

    val geminiService = if (geminiApiKey.nonEmpty) {
      Some(new GeminiService(geminiApiKey)(system, mat))
    } else {
      None
    }

    val aiSuggestionActor = system.actorOf(
      AISuggestionActor.props(qwenService, geminiService),
      "ai-suggestion"
    )

    val guardianActor = system.actorOf(
      GuardianActor.props(repo, aiSuggestionActor),
      "guardian"
    )

    val todoRoutes = new TodoRoutes(guardianActor, aiSuggestionActor, qwenService.isDefined, geminiService.isDefined)
    val routes: Route = todoRoutes.routes

    val host = config.getString("server.host")
    val port = config.getInt("server.port")

    val aiMsg = (qwenService.isDefined, geminiService.isDefined) match {
      case (true, true)   => "🤖 AI suggestions: Qwen (primary) + Gemini (fallback)"
      case (true, false)  => "🤖 AI suggestions: Qwen only (no GEMINI_API_KEY)"
      case (false, true)  => "🤖 AI suggestions: Gemini only (no QWEN_API_KEY)"
      case (false, false) => "⚠️  No AI configured - set QWEN_API_KEY or GEMINI_API_KEY"
    }

    val bindingFuture = Http(system).bindAndHandle(routes, host, port)

    println(s"🚀 Oursky Todo Backend started at http://$host:$port")
    println(aiMsg)

    sys.addShutdownHook {
      println("\n🛑 Shutting down...")
      Await.result(bindingFuture.flatMap(_.unbind()), 10.seconds)
      Await.result(system.terminate(), 10.seconds)
      db.close()
      println("✅ Shutdown complete")
    }

    Await.result(system.whenTerminated, Duration.Inf)
  }
}