package com.oursky.todo.db

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.oursky.todo.models.Subtask
import slick.jdbc.H2Profile
import slick.jdbc.JdbcBackend
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration._

class TodoRepositorySpec extends AnyWordSpec with BeforeAndAfterEach with ScalaFutures with Matchers {
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  private var db: JdbcBackend.Database = _
  private var repository: TodoRepository = _
  private var tables: Tables = _
  private var testCounter = 0

  def findByDepth(subtasks: List[Subtask], targetDepth: Int): Option[Subtask] = {
    subtasks.find(_.depth == targetDepth) orElse 
      subtasks.flatMap(s => findByDepth(s.subtasks, targetDepth)).headOption
  }

  override def beforeEach(): Unit = {
    testCounter += 1
    db = JdbcBackend.Database.forURL(s"jdbc:h2:mem:test$testCounter;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    tables = new Tables(H2Profile)
    Await.result(db.run(tables.createSchema), 10.seconds)
    repository = new TodoRepository(db, tables)
  }

  override def afterEach(): Unit = {
    if (db != null) db.close()
  }

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5000, Millis))

  "TodoRepository" should {
    
    "create a todo and return it with id" in {
      val todo = repository.create("Test Task").futureValue
      todo.id shouldBe 1L
      todo.title shouldBe "Test Task"
      todo.completed shouldBe false
    }

    "get all todos and return list" in {
      repository.create("Task 1").futureValue
      repository.create("Task 2").futureValue
      
      val todos = repository.getAll.futureValue
      todos.length shouldBe 2
    }

    "update todo title" in {
      val todo = repository.create("Original").futureValue
      val updated = repository.update(todo.id, Some("Updated"), None).futureValue
      
      updated.get.title shouldBe "Updated"
    }

    "update todo completed status" in {
      val todo = repository.create("Task").futureValue
      val updated = repository.update(todo.id, None, Some(true)).futureValue
      
      updated.get.completed shouldBe true
    }

    "add subtask to todo" in {
      val todo = repository.create("Parent Task").futureValue
      val updated = repository.addSubtask(todo.id, "Child Task", None).futureValue
      
      updated.get.subtasks.length shouldBe 1
      updated.get.subtasks.head.title shouldBe "Child Task"
      updated.get.subtasks.head.depth shouldBe 1
    }

    "delete todo" in {
      val todo = repository.create("To Delete").futureValue
      val deleted = repository.delete(todo.id).futureValue
      
      deleted shouldBe true
      
      val todos = repository.getAll.futureValue
      todos.length shouldBe 0
    }

    "return None when updating non-existent todo" in {
      val result = repository.update(999L, Some("Updated"), None).futureValue
      result shouldBe None
    }

    "return None when deleting non-existent todo" in {
      val result = repository.delete(999L).futureValue
      result shouldBe false
    }

    "add multiple levels of subtasks" in {
      val root = repository.create("Root").futureValue
      
      val level1 = repository.addSubtask(root.id, "Level 1", None).futureValue
      val l1Id = level1.get.subtasks.headOption.getOrElse(throw new Exception("No l1")).id
      
      val level2 = repository.addSubtask(root.id, "Level 2", Some(l1Id)).futureValue
      val l2Id = findByDepth(level2.get.subtasks, 2).get.id
      
      val level3 = repository.addSubtask(root.id, "Level 3", Some(l2Id)).futureValue
      
      findByDepth(level3.get.subtasks, 3).get.title shouldBe "Level 3"
    }

    "fail when subtask depth exceeds 5" in {
      val root = repository.create("Root").futureValue
      
      // Build up to level 5
      val l1 = repository.addSubtask(root.id, "L1", None).futureValue
      val l1Id = l1.get.subtasks.headOption.getOrElse(throw new Exception("No l1")).id
      
      val l2 = repository.addSubtask(root.id, "L2", Some(l1Id)).futureValue
      val l2Id = findByDepth(l2.get.subtasks, 2).get.id
      
      val l3 = repository.addSubtask(root.id, "L3", Some(l2Id)).futureValue
      val l3Id = findByDepth(l3.get.subtasks, 3).get.id
      
      val l4 = repository.addSubtask(root.id, "L4", Some(l3Id)).futureValue
      val l4Id = findByDepth(l4.get.subtasks, 4).get.id
      
      val l5 = repository.addSubtask(root.id, "L5", Some(l4Id)).futureValue
      val l5Id = findByDepth(l5.get.subtasks, 5).get.id
      
      // Level 6 should fail
      val ex = intercept[IllegalArgumentException] {
        Await.result(repository.addSubtask(root.id, "L6", Some(l5Id)), 5.seconds)
      }
      
      ex.getMessage should include("Maximum subtask depth")
    }

    "update subtask completed status" in {
      val todo = repository.create("Task").futureValue
      val updated = repository.addSubtask(todo.id, "Subtask", None).futureValue
      val subtaskId = updated.get.subtasks.headOption.getOrElse(throw new Exception("No subtask")).id
      
      val finalUpdate = repository.updateSubtask(todo.id, subtaskId, Some(true)).futureValue
      
      finalUpdate.get.subtasks.head.completed shouldBe true
    }

    "delete subtask" in {
      val todo = repository.create("Task").futureValue
      val updated = repository.addSubtask(todo.id, "To Remove", None).futureValue
      val subtaskId = updated.get.subtasks.headOption.getOrElse(throw new Exception("No subtask")).id
      
      val result = repository.deleteSubtask(todo.id, subtaskId).futureValue
      
      result.get.subtasks.length shouldBe 0
    }

    "get todo by id" in {
      val created = repository.create("Find Me").futureValue
      val found = repository.getById(created.id).futureValue
      
      found.get.title shouldBe "Find Me"
    }

    "return None for non-existent id" in {
      val result = repository.getById(999L).futureValue
      result shouldBe None
    }
  }
}