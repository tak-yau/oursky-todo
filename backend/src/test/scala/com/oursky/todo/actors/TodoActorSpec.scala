package com.oursky.todo.actors

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.oursky.todo.db.TodoRepository
import com.oursky.todo.{TodoCommand, TodoResponse}
import com.oursky.todo.models.Todo

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import org.mockito.Mockito.{mock, when}
import scala.language.reflectiveCalls

class TodoActorSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {
  
  implicit val system: ActorSystem = ActorSystem("test-system")
  implicit val ec: ExecutionContext = system.dispatcher
  
  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "TodoActor" should {
    
    "respond to GetAllTodos with list of todos" in {
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.getAll).thenReturn(scala.concurrent.Future.successful(List(
        Todo(1L, "Task 1", false, System.currentTimeMillis(), Nil),
        Todo(2L, "Task 2", true, System.currentTimeMillis(), Nil)
      )))
      
      val actor = system.actorOf(TodoActor.props(mockRepo))
      val probe = TestProbe()
      
      actor.tell(TodoCommand.GetAllTodos, probe.ref)
      
      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodosResponse(todos) =>
          todos.length shouldBe 2
      }
    }

    "respond to GetTodoById with todo" in {
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.getById(1L)).thenReturn(scala.concurrent.Future.successful(Some(
        Todo(1L, "Task 1", false, System.currentTimeMillis(), Nil)
      )))
      
      val actor = system.actorOf(TodoActor.props(mockRepo))
      val probe = TestProbe()
      
      actor.tell(TodoCommand.GetTodoById(1L), probe.ref)
      
      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.id shouldBe 1L
          todo.title shouldBe "Task 1"
      }
    }

    "respond to CreateTodo with new todo" in {
      val newTodo = Todo(1L, "New Task", false, System.currentTimeMillis(), Nil)
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.create("New Task")).thenReturn(scala.concurrent.Future.successful(newTodo))
      
      val actor = system.actorOf(TodoActor.props(mockRepo))
      val probe = TestProbe()
      
      actor.tell(TodoCommand.CreateTodo("New Task"), probe.ref)
      
      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.title shouldBe "New Task"
      }
    }

    "respond to UpdateTodo with updated todo" in {
      val updatedTodo = Todo(1L, "Updated", true, System.currentTimeMillis(), Nil)
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.update(1L, Some("Updated"), Some(true)))
        .thenReturn(scala.concurrent.Future.successful(Some(updatedTodo)))
      
      val actor = system.actorOf(TodoActor.props(mockRepo))
      val probe = TestProbe()
      
      actor.tell(TodoCommand.UpdateTodo(1L, Some("Updated"), Some(true)), probe.ref)
      
      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.title shouldBe "Updated"
      }
    }

    "respond to DeleteTodo with success boolean" in {
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.delete(1L)).thenReturn(scala.concurrent.Future.successful(true))
      
      val actor = system.actorOf(TodoActor.props(mockRepo))
      val probe = TestProbe()
      
      actor.tell(TodoCommand.DeleteTodo(1L), probe.ref)
      
      probe.expectMsgPF(5.seconds) {
        case TodoResponse.BooleanResponse(true) =>
          succeed
      }
    }

    "respond to AddSubtask with updated todo" in {
      val parentTodo = Todo(1L, "Parent", false, System.currentTimeMillis(), Nil)
      val updatedTodo = parentTodo.copy(subtasks = List(
        com.oursky.todo.models.Subtask(1L, "Child", false, Nil, 1)
      ))
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.addSubtask(1L, "Child", None))
        .thenReturn(scala.concurrent.Future.successful(Some(updatedTodo)))
      
      val actor = system.actorOf(TodoActor.props(mockRepo))
      val probe = TestProbe()
      
      actor.tell(TodoCommand.AddSubtask(1L, "Child", None), probe.ref)
      
      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.subtasks.length shouldBe 1
      }
    }

    "handle unknown messages with warning" in {
      val mockRepo = mock(classOf[TodoRepository])
      val actor = system.actorOf(TodoActor.props(mockRepo))
      
      actor ! "unknown"
      
      // Just verify no exception is thrown
      succeed
    }
  }
}