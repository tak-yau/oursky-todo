package com.oursky.todo

import com.typesafe.config.ConfigFactory
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.SyncBackend
import com.oursky.todo.db.{DB, TodoModel}
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object Main:

  def main(args: Array[String]): Unit =
    println("[MAIN] Starting...")  
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
      case _ =>
        throw IllegalArgumentException(s"Unsupported: $dbType")

    val qwenApiKey = sys.env.getOrElse("QWEN_API_KEY", "")
    val geminiApiKey = sys.env.getOrElse("GEMINI_API_KEY", "")

    val initSql = if dbType == "h2" then Some(
      """CREATE TABLE IF NOT EXISTS todo_row (
        |  id BIGINT AUTO_INCREMENT PRIMARY KEY,
        |  title VARCHAR(500) NOT NULL,
        |  completed BOOLEAN NOT NULL DEFAULT FALSE,
        |  created_at BIGINT NOT NULL
        |);
        |CREATE TABLE IF NOT EXISTS subtask_row (
        |  id BIGINT AUTO_INCREMENT PRIMARY KEY,
        |  todo_id BIGINT NOT NULL,
        |  title VARCHAR(500) NOT NULL,
        |  completed BOOLEAN NOT NULL DEFAULT FALSE,
        |  parent_id BIGINT,
        |  depth INT NOT NULL DEFAULT 1
        |);""".stripMargin) else None

    val db = DB.create(dataSource, initSql)
    val todoModel = new TodoModel(db)
    val todoService = new TodoService(todoModel, db)

    val httpBackend: SyncBackend = HttpClientSyncBackend()

    val qwenService = if qwenApiKey.nonEmpty then Some(new QwenService(httpBackend, qwenApiKey)) else None
    val geminiService = if geminiApiKey.nonEmpty then Some(new GeminiService(httpBackend, geminiApiKey)) else None

    val todoRoutes = new TodoRoutes(todoService, qwenService, geminiService)

    println("[MAIN] Starting Netty server...")
    
    NettySyncServer()
      .host("0.0.0.0")
      .port(8080)
      .addEndpoints(todoRoutes.endpoints)
      .startAndWait()