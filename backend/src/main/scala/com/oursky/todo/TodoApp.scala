package com.oursky.todo

import com.typesafe.config.ConfigFactory
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions}
import slick.jdbc.JdbcBackend
import slick.jdbc.H2Profile
import slick.jdbc.PostgresProfile
import com.oursky.todo.db.{Tables, TodoRepository}
import scala.concurrent.ExecutionContext
import java.net.http.HttpClient

@main def todoApp(): Unit = {
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

  val httpClient = HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofSeconds(10))
    .build()

  val qwenService = if (qwenApiKey.nonEmpty) {
    println(s"Qwen AI configured (model: qwen/qwen3.6-plus:free)")
    Some(new QwenService(httpClient, qwenApiKey))
  } else {
    None
  }

  val geminiService = if (geminiApiKey.nonEmpty) {
    println(s"Gemini AI configured (model: gemini-2.5-flash)")
    Some(new GeminiService(httpClient, geminiApiKey))
  } else {
    None
  }

  val todoRoutes = new TodoRoutes(todoService, qwenService, geminiService)

  val aiMsg = (qwenService.isDefined, geminiService.isDefined) match {
    case (true, true)   => "AI suggestions: Qwen (primary) + Gemini (fallback)"
    case (true, false)  => "AI suggestions: Qwen only"
    case (false, true)  => "AI suggestions: Gemini only"
    case (false, false) => "No AI configured - set QWEN_API_KEY or GEMINI_API_KEY"
  }

  println(s"Database: $dbLabel")
  println(s"Oursky Todo Backend starting on port 8080...")
  println(aiMsg)

  NettySyncServer()
    .port(8080)
    .addEndpoints(todoRoutes.endpoints)
    .startAndWait()
}
