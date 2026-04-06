package com.oursky.todo.actors

import org.apache.pekko.pattern.pipe
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.ActorLogging
import scala.concurrent.ExecutionContext
import com.oursky.todo.db.TodoRepository
import com.oursky.todo.{TodoCommand, TodoResponse}

class TodoActor(repo: TodoRepository)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import TodoCommand._
  import TodoResponse._

  override def receive: Receive = {
    case GetAllTodos =>
      log.info("Received GetAllTodos command")
      repo.getAll.map(todos => TodosResponse(todos)).pipeTo(sender())

    case GetTodoById(id: Long) =>
      log.info(s"Received GetTodoById command for id: $id")
      repo.getById(id).map(todo => TodoItemResponse(todo)).pipeTo(sender())

    case CreateTodo(title: String) =>
      log.info(s"Received CreateTodo command: $title")
      repo.create(title).map(todo => TodoItemResponse(Some(todo))).pipeTo(sender())

    case UpdateTodo(id: Long, title: Option[String], completed: Option[Boolean]) =>
      log.info(s"Received UpdateTodo command for id: $id")
      repo.update(id, title, completed).map(todo => TodoItemResponse(todo)).pipeTo(sender())

    case DeleteTodo(id: Long) =>
      log.info(s"Received DeleteTodo command for id: $id")
      repo.delete(id).map(success => BooleanResponse(success)).pipeTo(sender())

    case AddSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long]) =>
      log.info(s"Received AddSubtask command for todo: $todoId")
      repo.addSubtask(todoId, subtaskTitle, parentId).map(todo => TodoItemResponse(todo)).pipeTo(sender())

    case UpdateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean]) =>
      log.info(s"Received UpdateSubtask command for todo: $todoId, subtask: $subtaskId")
      repo.updateSubtask(todoId, subtaskId, completed).map(todo => TodoItemResponse(todo)).pipeTo(sender())

    case DeleteSubtask(todoId: Long, subtaskId: Long) =>
      log.info(s"Received DeleteSubtask command for todo: $todoId, subtask: $subtaskId")
      repo.deleteSubtask(todoId, subtaskId).map(todo => TodoItemResponse(todo)).pipeTo(sender())

    case unknown =>
      log.warning(s"Unknown command received: $unknown")
  }
}

object TodoActor {
  def props(repo: TodoRepository)(implicit ec: ExecutionContext): Props = Props(new TodoActor(repo))
}