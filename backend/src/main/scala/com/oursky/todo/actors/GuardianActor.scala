package com.oursky.todo.actors

import org.apache.pekko.actor.{Actor, ActorLogging, Props, OneForOneStrategy, SupervisorStrategy}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import com.oursky.todo.db.TodoRepository
import com.oursky.todo.{TodoCommand, TodoResponse, AICommand}
import org.apache.pekko.actor.ActorRef

class GuardianActor(
  repo: TodoRepository,
  aiSuggestionActor: ActorRef
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  val todoActor = context.actorOf(TodoActor.props(repo), "todo-actor")

  override val supervisorStrategy: SupervisorStrategy = {
    OneForOneStrategy(
      maxNrOfRetries = 10,
      withinTimeRange = 1.minute
    ) {
      case _: Exception =>
        log.error("Child actor failed, restarting...")
        SupervisorStrategy.Restart
    }
  }

  import TodoCommand._
  import TodoResponse._

  override def receive: Receive = {
    case GetAllTodos =>
      log.debug("Forwarding GetAllTodos to TodoActor")
      todoActor.forward(GetAllTodos)

    case GetTodoById(id) =>
      log.debug(s"Forwarding GetTodoById($id) to TodoActor")
      todoActor.forward(GetTodoById(id))

    case CreateTodo(title) =>
      log.debug(s"Forwarding CreateTodo($title) to TodoActor")
      todoActor.forward(CreateTodo(title))

    case UpdateTodo(id, title, completed) =>
      log.debug(s"Forwarding UpdateTodo($id) to TodoActor")
      todoActor.forward(UpdateTodo(id, title, completed))

    case DeleteTodo(id) =>
      log.debug(s"Forwarding DeleteTodo($id) to TodoActor")
      todoActor.forward(DeleteTodo(id))

    case AddSubtask(todoId, subtaskTitle, parentId) =>
      log.debug(s"Forwarding AddSubtask($todoId) to TodoActor")
      todoActor.forward(AddSubtask(todoId, subtaskTitle, parentId))

    case UpdateSubtask(todoId, subtaskId, completed) =>
      log.debug(s"Forwarding UpdateSubtask($todoId, $subtaskId) to TodoActor")
      todoActor.forward(UpdateSubtask(todoId, subtaskId, completed))

    case DeleteSubtask(todoId, subtaskId) =>
      log.debug(s"Forwarding DeleteSubtask($todoId, $subtaskId) to TodoActor")
      todoActor.forward(DeleteSubtask(todoId, subtaskId))

    case cmd: AICommand.GetAISuggestions =>
      log.debug(s"Forwarding GetAISuggestions to AISuggestionActor")
      aiSuggestionActor.forward(cmd)
  }
}

object GuardianActor {
  def props(
    repo: TodoRepository,
    aiSuggestionActor: ActorRef
  )(implicit ec: ExecutionContext): Props = Props(new GuardianActor(repo, aiSuggestionActor))
}