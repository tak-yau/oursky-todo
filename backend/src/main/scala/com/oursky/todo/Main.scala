package com.oursky.todo

import com.typesafe.config.ConfigFactory
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.SyncBackend
import com.oursky.todo.db.{DB, TodoModel}
import ox.OxApp
import ox.Ox
import ox.fork
import ox.supervised
import ox.*
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object Main extends OxApp.Simple:

  def run(using Ox): Unit =
    val config = ConfigFactory.load()
    val dbType = config.getString("database.type")
    val dbUrl = config.getString("database.url")
    val dbUser = config.getString("database.user")
    val dbPassword = config.getString("database.password")

    val dataSource: DataSource = dbType match
      case "h2" =>
        val ds = new HikariDataSource()
        ds.setJdbcUrl(dbUrl)
        ds.setUsername(dbUser)
        ds.setPassword(dbPassword)
        ds
      case "postgres" =>
        val ds = new HikariDataSource()
        ds.setJdbcUrl(dbUrl)
        ds.setUsername(dbUser)
        ds.setPassword(dbPassword)
        ds
      case other =>
        throw IllegalArgumentException(s"Unsupported database type: $other. Use 'h2' or 'postgres'.")

    val geminiApiKey = sys.env.getOrElse("GEMINI_API_KEY", "")
    val qwenApiKey = sys.env.getOrElse("QWEN_API_KEY", "")

    val initSql = dbType match
      case "h2" => Some(
        """CREATE TABLE IF NOT EXISTS todos (
          |  id BIGINT AUTO_INCREMENT PRIMARY KEY,
          |  title VARCHAR(500) NOT NULL,
          |  completed BOOLEAN NOT NULL DEFAULT FALSE,
          |  created_at BIGINT NOT NULL
          |);
          |CREATE TABLE IF NOT EXISTS subtasks (
          |  id BIGINT AUTO_INCREMENT PRIMARY KEY,
          |  todo_id BIGINT NOT NULL,
          |  title VARCHAR(500) NOT NULL,
          |  completed BOOLEAN NOT NULL DEFAULT FALSE,
          |  parent_id BIGINT,
          |  depth INT NOT NULL DEFAULT 1
          |);""".stripMargin)
      case _ => None

    val db = DB.create(dataSource, initSql)
    val todoModel = new TodoModel(db)
    val todoService = new TodoService(todoModel, db)

    val httpBackend: SyncBackend = HttpClientSyncBackend()

    val qwenService = if qwenApiKey.nonEmpty then
      debug(s"Qwen AI configured (model: qwen/qwen3.6-plus:free)")
      Some(new QwenService(httpBackend, qwenApiKey))
    else None

    val geminiService = if geminiApiKey.nonEmpty then
      debug(s"Gemini AI configured (model: gemini-2.5-flash)")
      Some(new GeminiService(httpBackend, geminiApiKey))
    else None

    val todoRoutes = new TodoRoutes(todoService, qwenService, geminiService)

    val aiMsg = (qwenService.isDefined, geminiService.isDefined) match
      case (true, true)   => "AI: Qwen (primary) + Gemini (fallback)"
      case (true, false)  => "AI: Qwen only"
      case (false, true)  => "AI: Gemini only"
      case (false, false) => "No AI configured"

    debug(s"Database: $dbType at $dbUrl")
    debug(aiMsg)
    debug("Starting server on port 8080...")

    supervised {
      val server = NettySyncServer()
        .port(8080)
        .addEndpoints(todoRoutes.endpoints)

      fork {
        server.startAndWait()
      }
    }
