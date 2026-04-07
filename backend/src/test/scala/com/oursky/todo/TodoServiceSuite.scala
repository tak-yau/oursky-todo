package com.oursky.todo

import munit.FunSuite
import com.oursky.todo.models.{Todo, Subtask}
import com.oursky.todo.db.{Tables, TodoRepository}
import slick.jdbc.H2Profile
import slick.jdbc.JdbcBackend
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration._

class TodoServiceSuite extends FunSuite {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private var service: TodoService = _
  private var db: JdbcBackend.Database = _
  private var testCounter = 0

  override def beforeEach(context: BeforeEach): Unit = {
    testCounter += 1
    db = JdbcBackend.Database.forURL(s"jdbc:h2:mem:test$testCounter;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    val tables = new Tables(H2Profile)
    Await.result(db.run(tables.createSchema), 10.seconds)
    val repo = new TodoRepository(db, tables)
    service = new TodoService(repo)
  }

  override def afterEach(context: AfterEach): Unit = {
    if (db != null) db.close()
  }

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
    assertEquals(updated.get.title, "Updated")
  }

  test("update todo should change completed status") {
    val todo = service.create("Task")
    val updated = service.update(todo.id, None, Some(true))
    assertEquals(updated.get.completed, true)
  }

  test("add subtask should work") {
    val todo = service.create("Parent Task")
    val updated = service.addSubtask(todo.id, "Child Task", None)
    assertEquals(updated.get.subtasks.length, 1)
    assertEquals(updated.get.subtasks.head.title, "Child Task")
    assertEquals(updated.get.subtasks.head.depth, 1)
  }

  test("delete todo should remove from list") {
    service.create("To Delete")
    val deleted = service.delete(1L)
    assertEquals(deleted, true)
    assertEquals(service.getAll.length, 0)
  }

  test("subtask depth should not exceed 4 levels") {
    val todo = service.create("Root")

    def findSubtaskByDepth(subtasks: List[Subtask], targetDepth: Int): Option[Subtask] = {
      subtasks.find(_.depth == targetDepth) match {
        case Some(st) => Some(st)
        case None => subtasks.flatMap(st => findSubtaskByDepth(st.subtasks, targetDepth)).headOption
      }
    }

    var parentId: Option[Long] = None
    for (i <- 1 to 4) {
      val updated = service.addSubtask(todo.id, s"Subtask level $i", parentId)
      parentId = updated.flatMap(t => findSubtaskByDepth(t.subtasks, i).map(_.id))
    }

    intercept[IllegalArgumentException] {
      service.addSubtask(todo.id, "Subtask level 5", parentId)
    }
  }

  test("data persists across service calls") {
    service.create("Persist Test")
    val todos = service.getAll
    assertEquals(todos.length, 1)
    assertEquals(todos.head.title, "Persist Test")
  }
}
