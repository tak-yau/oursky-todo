package com.oursky.todo

import munit.FunSuite
import com.oursky.todo.models.{Todo, Subtask}
import com.oursky.todo.db.{DB, TodoModel}
import com.augustnagro.magnum.*
import scala.util.Using
import javax.sql.DataSource
import com.zaxxer.hikari.HikariDataSource

class TodoServiceSuite extends FunSuite:

  private var service: TodoService = _
  private var db: DB = _
  private var ds: HikariDataSource = _

  private def createInMemoryDB(): (DB, HikariDataSource) =
    val dbName = java.util.UUID.randomUUID().toString.replace("-", "")
    val ds = HikariDataSource()
    ds.setJdbcUrl(s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1")
    ds.setUsername("sa")
    ds.setPassword("")
    ds.setMaximumPoolSize(1)
    val initSql = """CREATE TABLE IF NOT EXISTS todo_row (
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
          |);""".stripMargin
    (DB.create(ds, Some(initSql)), ds)

  override def beforeEach(context: BeforeEach): Unit =
    val (newDb, newDs) = createInMemoryDB()
    db = newDb
    ds = newDs
    val todoModel = new TodoModel(db)
    service = new TodoService(todoModel, db)

  override def afterEach(context: AfterEach): Unit =
    ds.close()

  test("create todo should return todo with id") {
    val todo = service.create("Test Task")
    assertEquals(todo.id, 1L)
    assertEquals(todo.title, "Test Task")
    assertEquals(todo.completed, false)
  }

  test("get all todos should return list") {
    service.create("Task 1")
    service.create("Task 2")
    val todos = service.getAll
    assertEquals(todos.length, 2)
  }

  test("update todo should change title") {
    val todo = service.create("Original")
    val updated = service.update(todo.id, Some("Updated"), None)
    assertEquals(updated match
      case Right(t) => t.title
      case Left(_) => "failed", "Updated")
  }

  test("update todo should change completed status") {
    val todo = service.create("Task")
    val updated = service.update(todo.id, None, Some(true))
    assertEquals(updated match
      case Right(t) => t.completed
      case Left(_) => false, true)
  }

  test("add subtask should work") {
    val todo = service.create("Parent Task")
    val updated = service.addSubtask(todo.id, "Child Task", None)
    assert(updated match
      case Right(_) => true
      case Left(_) => false)
  }

  test("delete todo should remove from list") {
    service.create("To Delete")
    val deleted = service.delete(1L)
    assertEquals(deleted match
      case Right(()) => true
      case Left(_) => false, true)
    assertEquals(service.getAll.length, 0)
  }

  test("data persists across service calls") {
    service.create("Persist Test")
    val todos = service.getAll
    assertEquals(todos.length, 1)
    assertEquals(todos.head.title, "Persist Test")
  }