package com.oursky.todo.actors

import org.apache.pekko.actor.{ActorSystem, Props, SupervisorStrategy}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.oursky.todo.db.TodoRepository
import com.oursky.todo.{TodoCommand, TodoResponse, AICommand, AIResponse}
import org.mockito.Mockito.{mock, when}
import com.oursky.todo.models.Todo
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import scala.language.reflectiveCalls

class GuardianActorSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {

  implicit val system: ActorSystem = ActorSystem("guardian-test-system")
  implicit val ec: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "GuardianActor" should {

    "forward GetAllTodos to TodoActor" in {
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.getAll).thenReturn(Future.successful(Nil))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian1")
      val probe = TestProbe()

      guardian.tell(TodoCommand.GetAllTodos, probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodosResponse(todos) =>
          todos shouldBe empty
      }
    }

    "forward CreateTodo to TodoActor" in {
      val newTodo = Todo(1L, "Test Task", false, System.currentTimeMillis(), Nil)
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.create("Test Task")).thenReturn(Future.successful(newTodo))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian2")
      val probe = TestProbe()

      guardian.tell(TodoCommand.CreateTodo("Test Task"), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.title shouldBe "Test Task"
      }
    }

    "forward UpdateTodo to TodoActor" in {
      val updatedTodo = Todo(1L, "Updated", true, System.currentTimeMillis(), Nil)
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.update(1L, Some("Updated"), Some(true))).thenReturn(Future.successful(Some(updatedTodo)))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian3")
      val probe = TestProbe()

      guardian.tell(TodoCommand.UpdateTodo(1L, Some("Updated"), Some(true)), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.title shouldBe "Updated"
      }
    }

    "forward DeleteTodo to TodoActor" in {
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.delete(1L)).thenReturn(Future.successful(true))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian4")
      val probe = TestProbe()

      guardian.tell(TodoCommand.DeleteTodo(1L), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.BooleanResponse(true) =>
          succeed
      }
    }

    "forward GetTodoById to TodoActor" in {
      val todo = Todo(1L, "Test", false, System.currentTimeMillis(), Nil)
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.getById(1L)).thenReturn(Future.successful(Some(todo)))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian5")
      val probe = TestProbe()

      guardian.tell(TodoCommand.GetTodoById(1L), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(t)) =>
          t.id shouldBe 1L
      }
    }

    "forward AddSubtask to TodoActor" in {
      val parentTodo = Todo(1L, "Parent", false, System.currentTimeMillis(), Nil)
      val updatedTodo = parentTodo.copy(subtasks = List(
        com.oursky.todo.models.Subtask(1L, "Child", false, Nil, 1)
      ))
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.addSubtask(1L, "Child", None)).thenReturn(Future.successful(Some(updatedTodo)))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian6")
      val probe = TestProbe()

      guardian.tell(TodoCommand.AddSubtask(1L, "Child", None), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.subtasks.length shouldBe 1
      }
    }

    "forward UpdateSubtask to TodoActor" in {
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.updateSubtask(1L, 1L, Some(true))).thenReturn(Future.successful(Some(
        Todo(1L, "Test", true, System.currentTimeMillis(), Nil)
      )))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian7")
      val probe = TestProbe()

      guardian.tell(TodoCommand.UpdateSubtask(1L, 1L, Some(true)), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.id shouldBe 1L
      }
    }

    "forward DeleteSubtask to TodoActor" in {
      val mockRepo = mock(classOf[TodoRepository])
      when(mockRepo.deleteSubtask(1L, 1L)).thenReturn(Future.successful(Some(
        Todo(1L, "Test", true, System.currentTimeMillis(), Nil)
      )))

      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian8")
      val probe = TestProbe()

      guardian.tell(TodoCommand.DeleteSubtask(1L, 1L), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case TodoResponse.TodoItemResponse(Some(todo)) =>
          todo.id shouldBe 1L
      }
    }

    "forward GetAISuggestions to AISuggestionActor" in {
      val mockRepo = mock(classOf[TodoRepository])
      val aiActor = TestProbe()
      val guardian = system.actorOf(GuardianActor.props(mockRepo, aiActor.ref), "guardian9")
      val probe = TestProbe()

      guardian.tell(AICommand.GetAISuggestions("test context", false), probe.ref)

      aiActor.expectMsgPF(5.seconds) {
        case AICommand.GetAISuggestions("test context", false) => succeed
      }
    }
  }
}