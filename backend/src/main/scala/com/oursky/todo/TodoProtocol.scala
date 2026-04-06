package com.oursky.todo

import com.oursky.todo.models.{Todo, Subtask}

sealed trait TodoCommand

object TodoCommand {
  case object GetAllTodos extends TodoCommand
  case class GetTodoById(id: Long) extends TodoCommand
  case class CreateTodo(title: String) extends TodoCommand
  case class UpdateTodo(id: Long, title: Option[String], completed: Option[Boolean]) extends TodoCommand
  case class DeleteTodo(id: Long) extends TodoCommand
  case class AddSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long]) extends TodoCommand
  case class UpdateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean]) extends TodoCommand
  case class DeleteSubtask(todoId: Long, subtaskId: Long) extends TodoCommand
}

sealed trait TodoResponse

object TodoResponse {
  case class TodosResponse(todos: List[Todo]) extends TodoResponse
  case class TodoItemResponse(todo: Option[Todo]) extends TodoResponse
  case class BooleanResponse(success: Boolean) extends TodoResponse
}

sealed trait AICommand

object AICommand {
  case class GetAISuggestions(context: String, isSubtask: Boolean) extends AICommand
}

sealed trait AIResponse

object AIResponse {
  case class AISuggestionsResponse(suggestions: List[String]) extends AIResponse
  case class AIErrorResponse(error: String) extends AIResponse
}