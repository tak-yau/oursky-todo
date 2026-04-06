package com.oursky.todo

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.testkit.TestProbe
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.oursky.todo.actors.{GuardianActor, TodoActor, AISuggestionActor}
import com.oursky.todo.db.TodoRepository
import com.oursky.todo.{TodoCommand, TodoResponse, AICommand, AIResponse}
import com.oursky.todo.models.{Todo, Subtask}
import scala.concurrent.{Future, ExecutionContext}
import scala.language.reflectiveCalls
import org.mockito.Mockito.{mock, when}

class TodoRoutesSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers with ScalatestRouteTest {

  import scala.concurrent.ExecutionContext.Implicits.global
  val mockRepo: TodoRepository = mock(classOf[TodoRepository])
  val aiSuggestionActor = system.actorOf(AISuggestionActor.props(None, None), "ai-suggestion")
  val guardian = system.actorOf(GuardianActor.props(mockRepo, aiSuggestionActor), "guardian")
  val routes = new TodoRoutes(guardian, aiSuggestionActor, qwenEnabled = false, geminiEnabled = false).routes

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "TodoRoutes" should {

    "return health check OK" in {
      Get("/health") ~> routes ~> check {
        responseAs[String] should include("ok")
      }
    }

    "return all todos" in {
      when(mockRepo.getAll).thenReturn(Future.successful(List(
        Todo(1L, "Task 1", false, System.currentTimeMillis(), Nil),
        Todo(2L, "Task 2", true, System.currentTimeMillis(), Nil)
      )))

      Get("/api/todos") ~> routes ~> check {
        responseAs[String] should include("Task 1")
        responseAs[String] should include("Task 2")
      }
    }

    "return empty list when no todos" in {
      when(mockRepo.getAll).thenReturn(Future.successful(Nil))

      Get("/api/todos") ~> routes ~> check {
        responseAs[String] shouldBe "[]"
      }
    }

    "return todo by id" in {
      when(mockRepo.getById(1L)).thenReturn(Future.successful(Some(
        Todo(1L, "Task 1", false, System.currentTimeMillis(), Nil)
      )))

      Get("/api/todos/1") ~> routes ~> check {
        responseAs[String] should include("Task 1")
      }
    }

    "return 404 for non-existent todo" in {
      when(mockRepo.getById(999L)).thenReturn(Future.successful(None))

      Get("/api/todos/999") ~> routes ~> check {
        status.intValue() shouldBe 404
      }
    }

    "delete todo successfully" in {
      when(mockRepo.delete(1L)).thenReturn(Future.successful(true))

      Delete("/api/todos/1") ~> routes ~> check {
        status.intValue() shouldBe 204
      }
    }

    "return 404 when deleting non-existent todo" in {
      when(mockRepo.delete(999L)).thenReturn(Future.successful(false))

      Delete("/api/todos/999") ~> routes ~> check {
        status.intValue() shouldBe 404
      }
    }

    "return AI suggestions error when no service configured" in {
      Post("/api/ai/suggestions", """{"title": "test"}""") ~> routes ~> check {
        status.intValue() shouldBe 503
      }
    }

    "create todo successfully" in {
      val newTodo = Todo(1L, "New Task", false, System.currentTimeMillis(), Nil)
      when(mockRepo.create("New Task")).thenReturn(Future.successful(newTodo))

      Post("/api/todos", """{"title":"New Task"}""") ~> routes ~> check {
        status.intValue() shouldBe 201
        responseAs[String] should include("New Task")
      }
    }

    "return 400 when creating todo with invalid JSON" in {
      Post("/api/todos", """{invalid json}""") ~> routes ~> check {
        status.intValue() shouldBe 400
      }
    }

    "update todo successfully" in {
      val updatedTodo = Todo(1L, "Updated", true, System.currentTimeMillis(), Nil)
      when(mockRepo.update(1L, Some("Updated"), Some(true))).thenReturn(Future.successful(Some(updatedTodo)))

      Put("/api/todos/1", """{"title":"Updated","completed":true}""") ~> routes ~> check {
        status.intValue() shouldBe 200
        responseAs[String] should include("Updated")
      }
    }

    "return 404 when updating non-existent todo" in {
      when(mockRepo.update(999L, Some("Updated"), None)).thenReturn(Future.successful(None))

      Put("/api/todos/999", """{"title":"Updated"}""") ~> routes ~> check {
        status.intValue() shouldBe 404
      }
    }

    "return 400 when updating todo with invalid JSON" in {
      Put("/api/todos/1", """{invalid}""") ~> routes ~> check {
        status.intValue() shouldBe 400
      }
    }

    "add subtask successfully" in {
      val todoWithSubtask = Todo(1L, "Parent", false, System.currentTimeMillis(), List(
        Subtask(1L, "Child", false, Nil, 1)
      ))
      when(mockRepo.addSubtask(1L, "Child", None)).thenReturn(Future.successful(Some(todoWithSubtask)))

      Post("/api/todos/1/subtasks", """{"subtaskTitle":"Child"}""") ~> routes ~> check {
        status.intValue() shouldBe 200
        responseAs[String] should include("Child")
      }
    }

    "return 404 when adding subtask to non-existent todo" in {
      when(mockRepo.addSubtask(999L, "Child", None)).thenReturn(Future.successful(None))

      Post("/api/todos/999/subtasks", """{"subtaskTitle":"Child"}""") ~> routes ~> check {
        status.intValue() shouldBe 404
      }
    }

    "return 400 when adding subtask with invalid JSON" in {
      Post("/api/todos/1/subtasks", """{bad}""") ~> routes ~> check {
        status.intValue() shouldBe 400
      }
    }

    "update subtask successfully" in {
      val todoWithSubtask = Todo(1L, "Parent", false, System.currentTimeMillis(), List(
        Subtask(1L, "Child", true, Nil, 1)
      ))
      when(mockRepo.updateSubtask(1L, 1L, Some(true))).thenReturn(Future.successful(Some(todoWithSubtask)))

      Put("/api/todos/1/subtasks/1", """{"completed":true}""") ~> routes ~> check {
        status.intValue() shouldBe 200
        responseAs[String] should include("Parent")
      }
    }

    "return 404 when updating non-existent subtask" in {
      when(mockRepo.updateSubtask(1L, 999L, Some(true))).thenReturn(Future.successful(None))

      Put("/api/todos/1/subtasks/999", """{"completed":true}""") ~> routes ~> check {
        status.intValue() shouldBe 404
      }
    }

    "return 400 when updating subtask with invalid JSON" in {
      Put("/api/todos/1/subtasks/1", """{bad}""") ~> routes ~> check {
        status.intValue() shouldBe 400
      }
    }

    "delete subtask successfully" in {
      val todoAfterDelete = Todo(1L, "Parent", false, System.currentTimeMillis(), Nil)
      when(mockRepo.deleteSubtask(1L, 1L)).thenReturn(Future.successful(Some(todoAfterDelete)))

      Delete("/api/todos/1/subtasks/1") ~> routes ~> check {
        status.intValue() shouldBe 204
      }
    }

    "return 404 when deleting non-existent subtask" in {
      when(mockRepo.deleteSubtask(1L, 999L)).thenReturn(Future.successful(None))

      Delete("/api/todos/1/subtasks/999") ~> routes ~> check {
        status.intValue() shouldBe 404
      }
    }
  }
}